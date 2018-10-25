// Copyright (c) YugaByte, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.  You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the License
// is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
// or implied.  See the License for the specific language governing permissions and limitations
// under the License.
//

package com.yb.connect.sink;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;


import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;
import org.apache.kafka.connect.data.Struct;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.ColumnMetadata;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.TableMetadata;
import com.datastax.driver.core.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * YugaByte Sink Task.
 */
public class YBSinkTask extends SinkTask {
    private static final Logger LOG = LoggerFactory.getLogger(YBSinkTask.class);
    private static final String VERSION = "1";

    private String keyspace;
    private String tablename;
    private Cluster cassandraCluster = null;
    private Session cassandraSession = null;
    private Map<String, DataType> columnNameToType;

    @Override
    public void start(final Map<String, String> properties) {
        this.keyspace = properties.get("yugabyte.cql.keyspace");
        this.tablename = properties.get("yugabyte.cql.tablename");
        LOG.info("START " + this.keyspace + " " + this.tablename);
        this.cassandraSession = getCassandraSession(properties);
        columnNameToType = new HashMap<String, DataType>();
    }

    // Used to store schema information from the table.
    private class ColumnInfo {
        private String name;
        private DataType dataType;

        ColumnInfo(String colname, DataType datatype) {
            name = colname;
            dataType = datatype;
        }
    }

    // Used to store contact point information to the YugaByte cluster.
    private class ContactPoint {
        private String host;
        private int port;

        public ContactPoint(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String ToString() { return host + ":" + port; }
    }

    public ContactPoint fromHostPort(String hostPort) {
        String[] parts = hostPort.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid host port format " + hostPort);
        }
        return new ContactPoint(parts[0], Integer.parseInt(parts[1]));
    }

    private List<ContactPoint> getContactPoints(String csvHostPorts) {
        if (csvHostPorts == null) {
            throw new IllegalArgumentException("Invalid null cql contact point list");
        }
        List<ContactPoint> contactPoints = new ArrayList<ContactPoint>();
        String[] hostPorts = csvHostPorts.split(",");
        for (String hostPort : hostPorts) {
            contactPoints.add(fromHostPort(hostPort));
        }
        return contactPoints;
    }

    protected Session getCassandraSession(final Map<String, String> properties) {
        if (cassandraSession == null) {
            createCassandraClient(getContactPoints(properties.get("yugabyte.cql.contact.points")));
        }
        return cassandraSession;
    }

    /**
     * Create a Cassandra client and session.
     */
    private synchronized void createCassandraClient(List<ContactPoint> contactPoints) {
        if (cassandraCluster == null) {
            Cluster.Builder builder = Cluster.builder();
            Integer port = null;
            for (ContactPoint cp : contactPoints) {
               if (port == null) {
                    port = cp.getPort();
                    builder.withPort(port);
                } else if (port != cp.getPort()) {
                    throw new IllegalArgumentException("Using multiple CQL ports is not supported.");
                }
                builder.addContactPoint(cp.getHost());
            }
            LOG.info("Connecting to nodes: " + builder.getContactPoints().stream()
                     .map(it -> it.toString()).collect(Collectors.joining(",")));
            cassandraCluster = builder.build();
        }
        if (cassandraSession == null) {
            cassandraSession = cassandraCluster.connect();
            LOG.info("Connected to cluster: " + cassandraCluster.getClusterName());
        }
    }

    private String generateInsert(List<ColumnInfo> cols) {
        String insert = "INSERT INTO " + keyspace + "." + tablename + "(" + cols.get(0).name;
        String comma = ",";
        String quesMarks = "?";
        for (int i = 1; i < cols.size(); i++) {
            insert = insert + comma + cols.get(i).name;
            quesMarks = quesMarks + comma + "?";
        }
        insert = insert + ") VALUES (" + quesMarks + ")";
        return insert;
    }

    private String generateColNameInsert(List<ColumnInfo> cols) {
        String insert = "INSERT INTO " + keyspace + "." + tablename + "(" + cols.get(0).name;
        String colon = ":";
        String comma = ",";
        String binds = colon + cols.get(0).name;
        for (int i = 1; i < cols.size(); i++) {
            insert = insert + comma + cols.get(i).name;
            binds = binds + comma + colon + cols.get(i).name;
        }
        insert = insert + ") VALUES (" + binds + ")";
        return insert;
    }

    private List<ColumnInfo> getColsFromTable() {
        KeyspaceMetadata km = cassandraSession.getCluster().getMetadata().getKeyspace(keyspace);
        if (null == km) {
            throw new IllegalArgumentException("Keyspace " + keyspace + " not found.");
        }
        TableMetadata tm = km.getTable(tablename);
        if (null == tm) {
            throw new IllegalArgumentException("Table " + tablename + " not found.");
        }
        List<ColumnInfo> cols = new ArrayList<ColumnInfo>();
        for (ColumnMetadata cm : tm.getColumns()) {
            cols.add(new ColumnInfo(cm.getName(), cm.getType()));
            columnNameToType.put(cm.getName(), cm.getType());
            LOG.info("Add column " + cm.getName() + " of type " + cm.getType());
        }
        return cols;
    }

    private BoundStatement bindByColumnType(BoundStatement statement, String colName,
                                            DataType.Name typeName,  Object value) {
        LOG.info("Bind " + colName + " of type " + typeName);
        switch (typeName) {
            case INT:
                statement = statement.setInt(colName, (Integer)value);
                break;
            case BIGINT:
                statement = statement.setLong(colName, (Long)value);
                break;
            case FLOAT:
                statement = statement.setFloat(colName, (Float)value);
                break;
            case DOUBLE:
                statement = statement.setDouble(colName, (Double)value);
                break;
            case VARCHAR:
            case TEXT:
                statement = statement.setString(colName, (String)value);
                break;
            case BOOLEAN:
                statement = statement.setBool(colName, (Boolean)value);
                break;
            case BLOB:
                ByteBuffer bb = null;
                if (value instanceof ByteBuffer) {
                    bb = (ByteBuffer)value;
                } else {
                    bb = ByteBuffer.wrap((byte[])value);
                }
                statement = statement.setBytes(colName, bb);
                break;
            case TIMESTAMP:
                if (value instanceof String) {
                    // TODO: Make helper function. Allow more formats.
                    Calendar cal = new GregorianCalendar();
                    cal.setTimeZone(TimeZone.getTimeZone("GMT"));
                    cal.setTimeInMillis(0);
                    String val = (String) value;
                    String[] dateTime = val.split(" ");
                    String errMsg = "Invalid timestamp format. Expect yyyy-mm-dd hh:mm:ss";
                    if (dateTime.length != 2) {
                        throw new IllegalArgumentException(errMsg);
                    }
                    String[] date = dateTime[0].split("-");
                    if (date.length != 3) {
                        throw new IllegalArgumentException(errMsg);
                    }
                    // Java Date month value starts at 0 not 1.
                    cal.set(Integer.parseInt(date[0]), Integer.parseInt(date[1]) - 1,
                            Integer.parseInt(date[2]));
                    String[] time = dateTime[1].split(":");
                    if (date.length > 4 || date.length < 2) {
                        throw new IllegalArgumentException(errMsg);
                    }

                    cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(time[0]));
                    cal.set(Calendar.MINUTE, Integer.parseInt(time[1]));
                    if (date.length == 3) {
                        cal.set(Calendar.SECOND, Integer.parseInt(time[2]));
                    }
                    LOG.info("Cal " + cal.toString());
                    statement = statement.setTimestamp(colName, cal.getTime());
                } else if (value instanceof Integer) {
                    Calendar cal = new GregorianCalendar();
                    Long val = (Long) value;
                    cal.setTimeInMillis(val);
                    statement = statement.setTimestamp(colName, cal.getTime());
                } else {
                    String errMsg = "Timestamp can be set as string or integer only.";
                    LOG.info(errMsg);
                    throw new IllegalArgumentException(errMsg);
                }
                break;
            default:
                String errMsg = "Column type " + typeName + " for " + colName +
                                " not supported yet.";
                LOG.warn(errMsg);
                throw new IllegalArgumentException(errMsg);
        }
        return statement;
    }

    private BoundStatement bindByType(BoundStatement statement, Schema schema, Object value,
                                      DataType colType) {
        switch (schema.type()) {
          case INT8:
            statement = statement.setInt(schema.name(), (Byte)value);
            break;
          case INT16:
            statement = statement.setShort(schema.name(), (Short)value);
            break;
          case INT32:
            statement = statement.setInt(schema.name(), (Integer)value);
            break;
          case INT64:
            statement = statement.setLong(schema.name(), (Long)value);
            break;
          case FLOAT32:
            statement = statement.setFloat(schema.name(), (Float)value);
            break;
          case FLOAT64:
            statement = statement.setDouble(schema.name(), (Double)value);
            break;
          case BOOLEAN:
            statement = statement.setBool(schema.name(), (Boolean)value);
            break;
          case STRING:
            // TODO: Check the table type and convert types like timestamp.
            // if (colType == TIMESTAMP) { } else {
            statement = statement.setString(schema.name(), (String)value);
            break;
          case BYTES:
            ByteBuffer bb = null;
            if (value instanceof ByteBuffer) {
                bb = (ByteBuffer)value;
            } else {
                bb = ByteBuffer.wrap((byte[])value);
            }
            statement = statement.setBytes(schema.name(), bb);
            break;
          default:
            String errMsg = "Schema type " + schema.type() + " for " + schema.name() +
                            " not supported yet.";
            LOG.warn(errMsg);
            throw new IllegalArgumentException(errMsg);
        }
        return statement;
    }

    private BoundStatement bindFields(PreparedStatement statement,
                                      final SinkRecord record) {
        BoundStatement bound = statement.bind();
        Map<String, Object> valueMap = ((HashMap<String, Object>) record.value());
        // TODO: This will ignore two columns with just caps difference in the name.
        Map<String, String> lowerToOrig = new HashMap<String, String>();
        for (String key : valueMap.keySet()) {
            if (lowerToOrig.get(key.toLowerCase()) != null) {
                throw new IllegalArgumentException("Column name " + key + " with different " +
                    " capsitalization already present " + lowerToOrig.get(key.toLowerCase()));
            }
            lowerToOrig.put(key.toLowerCase(), key);
        }
        for (final String colName : columnNameToType.keySet()) {
            Object value = valueMap.get(lowerToOrig.get(colName));
            if (value == null) {
                LOG.info("Entry for table column " + colName + " not found in sink record.");
                bound = bound.setString(colName, null);
            } else {
                bound = bindByColumnType(bound, colName, columnNameToType.get(colName).getName(),
                                         value);
            }
        }
        return bound;
    }

    private BoundStatement bindFields(PreparedStatement statement,
                                      final Map<String, SchemaAndValue> allFields) {
        BoundStatement bound = statement.bind();
        for (final String colName : columnNameToType.keySet()) {
            final SchemaAndValue sav = allFields.get(colName);
            if (sav == null) {
                LOG.info("Entry for table column " + colName + " not found in sink record.");
                bound = bound.setString(colName, null);
            } else {
                bound = bindByType(bound, sav.schema, sav.value, columnNameToType.get(colName));
            }
        }
        return bound;
    }

    // Helper struct to track per field schema and value.
    class SchemaAndValue {
        private Schema schema;
        private Object value;

        public SchemaAndValue(Schema schema, Object value) {
            this.schema = schema;
            this.value = value;
        }
    }

    private List<Statement> getStatements(final Collection<SinkRecord> records) {
        List<Statement> boundStatements = new ArrayList<Statement>();
        String insert = generateInsert(getColsFromTable());
        PreparedStatement preparedStatement = cassandraSession.prepare(insert);
        LOG.info("Insert " + insert);
        for (SinkRecord record : records) {
            LOG.info("Prepare " + record + " Key/Schema=" + record.key() + "/" + record.keySchema()
                     + " Value/Schema=" + record.value() + "/" + record.valueSchema());
            if (record.value() == null) {
                throw new IllegalArgumentException("Invalid `value` in " + record);
            }
            if (record.valueSchema() == null) {
                boundStatements.add(bindFields(preparedStatement, record));
                continue;
            }
            if (record.valueSchema().type() != Schema.Type.MAP) {
                throw new IllegalArgumentException("Invalid schema for value " +
                    record.valueSchema().type() + " expected a map.");
            }
            final Map<String, SchemaAndValue> allFields = new HashMap<String, SchemaAndValue>();
            for (Field field : record.valueSchema().fields()) {
                allFields.put(field.name(),
                    new SchemaAndValue(field.schema(), ((Struct) record.value()).get(field)));
                LOG.info("Added field " + field.name() + " of type "+ field.schema().type());
            }
            boundStatements.add(bindFields(preparedStatement, allFields));
        }
        return boundStatements;
    }

    @Override
    public void put(final Collection<SinkRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        LOG.info("Processing " + records.size() + " records from Kafka.");
        List<Statement> statements = getStatements(records);
        statements.forEach(statement -> cassandraSession.execute(statement));
    }

    @Override
    public void stop() {
        if (cassandraSession != null) {
            cassandraSession.close();
        }
        if (cassandraCluster != null) {
            cassandraCluster.close();
        }
    }

    @Override
    public String version() {
        return VERSION;
    }
}
