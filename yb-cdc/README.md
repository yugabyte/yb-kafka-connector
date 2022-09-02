Using YugabyteDB’s Change Data Capture (CDC) API, follow these steps to use YugabyteDB as a data source to a Kafka or console sink: 

SETUP YUGABYTEDB

1. Install YugabyteDB.

https://docs.yugabyte.com/latest/quick-start/install/

2. Create a local cluster.

https://docs.yugabyte.com/latest/quick-start/create-local-cluster/

3. Create a table on the cluster.

SETUP KAFKA (Skip if Logging to Console):

Avro Schemas:

We support the use of avro schemas to serialize/deserialize tables. Create two avro schemas, one for the table and one for the primary key of the table. After this step, you should have two files: table_schema_path.avsc and primary_key_schema_path.avsc

https://www.tutorialspoint.com/avro/avro_schemas.htm

Starting the kafka services:

1. First, download confluent.

```
curl -O http://packages.confluent.io/archive/5.3/confluent-5.3.1-2.12.tar.gz
tar -xvzf confluent-5.3.1-2.12.tar.gz
cd confluent-5.3.1/
```

2. Download the bin directory and add it to the PATH var.

```
curl -L https://cnfl.io/cli | sh -s -- -b /<path-to-directory>/bin
export PATH=<path-to-confluent>/bin:$PATH
export CONFLUENT_HOME=~/code/confluent-5.3.1
```

3. Start the zookeeper, kafka, and the avro schema registry services.

```
./bin/confluent local start
```

4. Create a kafka topic.

```
./bin/kafka-topics --create --partitions 1 --topic <topic_name> --bootstrap-server localhost:9092 --replication-factor 1
```

5. Start the kafka consumer

```
bin/kafka-avro-console-consumer --bootstrap-server localhost:9092 --topic <topic_name> --key-deserializer=io.confluent.kafka.serializers.KafkaAvroDeserializer --value-deserializer=io.confluent.kafka.serializers.KafkaAvroDeserializer
```

SETUP YB CONNECTOR:

1. In a new window, fork YugabyteDB’s kafka connector repository.

```
git clone https://github.com/yugabyte/yb-kafka-connector.git
cd yb-kafka-connector/yb-cdc
```

2. Start the kafka connector app.

Logging to console:

```
java -jar yb_cdc_connector.jar
--table_name <namespace/database>.<table>
--master_addrs <yb master addresses> [default 127.0.0.1:7100]
--[stream_id] <optional existing stream id>
--log_only // Flag to log to console.
```

Logging to Kafka:

```
java -jar yb_cdc_connector.jar
--table_name <namespace/database>.<table>
--master_addrs <yb master addresses> [default 127.0.0.1:7100]
--[stream_id] <optional existing stream id>
--kafka_addrs <kafka cluster addresses> [default 127.0.0.1:9092]
--shema_registry_addrs [default 127.0.0.1:8081]
--topic_name <topic name to write to>
--table_schema_path <avro table schema>
--primary_key_schema_path <avro primary key schema>
```

3. In another window, write values to the table and observe the values on your chosen output stream.

