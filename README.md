# Kafka Connect to YugaByte DB

This is the framework to connect Kafka streams to use [YugaByte DB](https://github.com/YugaByte/yugabyte-db) as the data sink using the [Kafka Connect](https://docs.confluent.io/3.0.0/connect/intro.html) mechanism.
NOTE: This is a highly work in progress repo and the instructions/steps below can change.

## Prerequisites

For building these projects it requires following tools. Please refer README.md files of individual projects for more details.
- JDK - 1.8+
- Maven - 3.3+
- Cassandra Core driver - 3.2.0
- Confluent Kafka (we assume this is installed in the `~/code/confluent-os/confluent-5.0.0` directory).

## Steps to setup and run connect sink
1. Download Confluent Open Source from https://www.confluent.io/download/. Unbundle the content of the tar.gz to say ```~/code/confluent-os/confluent-5.0.0```.

2. Include the YugaByte DB sink into the registered connectors:
  - Add the "yb-sink=kafka-connect-yugabyte/sink.properties" in bin/confluent file within the `declare -a connector_properties={` scope.
  - Copy the sample kafka-sink properties file from this repos `resources/examples/kafka.connector` to `~/code/confluent-os/confluent-5.0.0/etc/kafka-connect-yugabyte/`
  - For now, set the following in the ```~/code/confluent-os/confluent-5.0.0/etc/kafka/connect-standalone.properties``` file
    ```key.converter.schemas.enable=false
    value.converter.schemas.enable=false```

3. Include dependent jar into kafka connectors:
  - Run 'mvn clean install -DskipTests' in the top-level directory in this repo and copy the jar file:
    ```mkdir ~/code/confluent-os/confluent-5.0.0/share/java/kafka-connect-yugabyte/; cp  ~/code/yb-kafka-connector/target/yb-kafka-connnector-1.0.0.jar ~/code/confluent-os/confluent-5.0.0/share/java/kafka-connect-yugabyte/```
  - Copy all the dependent jars from Cassandra into the same folder. Most of these can be downloaded from maven central repository. The final list of jars should look like this:
    ```-rw-r--r--@    85449 Oct 27  2013 metrics-core-3.0.1.jar
       -rw-r--r--   1082699 Oct 26 00:16 cassandra-driver-core-3.2.0.jar
       -rw-r--r--@  3823147 Oct 27 15:18 netty-all-4.1.25.Final.jar
       -rw-r--r--     13917 Oct 27 15:41 yb-kafka-connnector-1.0.0.jar
     ```

4. Do the following to run Kafka and related dependencies:
export PATH=$PATH:~/code/confluent-os/confluent-5.0.0/bin
confluent start
confluent stop connect
confluent stop kafka-rest
confluent status

The output for this should look like
```
control-center is [UP]
ksql-server is [UP]
connect is [DOWN]
kafka-rest is [DOWN]
schema-registry is [UP]
kafka is [UP]
zookeeper is [UP]
```

5. Install YugaByte DB and create the keyspace/table.
- [Install YugaByte DB and start a local cluster](https://docs.yugabyte.com/quick-start/install/).
- Create the keyspace and table by running the following command. You can find `cqlsh` in the `bin` sub-directory located inside the YugaByte installation folder.
```sh
cqlsh -f resources/examples/table.cql
```

6. Create a topic (one time)
```~/code/confluent-os/confluent-5.0.0/bin/kafka-topics --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic iot-data-event```
and then run the sample Kafka producer java app (need to tweak this to not depend on the https://github.com/YugaByte/yb-iot-fleet-management repo):
cd ~/code/yb-iot-fleet-management; java -jar iot-kafka-producer/target/iot-kafka-producer-1.0.0.jar

7. Run the Kafka Connect in standalone mode
```cd ~/code/confluent-os/confluent-5.0.0; ./bin/connect-standalone ./etc/kafka/connect-standalone.properties ./etc/kafka-connect-yugabyte/sink.properties```
Note: In the connect-standalone need to set *.enable.schema to false.

You should see something like this (relevant lines from YBSinkTask.java):
```
[2018-10-28 16:24:16,037] INFO START iotdemo ingress (com.yb.connect.sink.YBSinkTask:69)
[2018-10-28 16:24:16,054] INFO Connecting to nodes: /127.0.0.1:9042,/127.0.0.2:9042,/127.0.0.3:9042 (com.yb.connect.sink.YBSinkTask:149)
[2018-10-28 16:24:16,517] INFO Connected to cluster: cluster1 (com.yb.connect.sink.YBSinkTask:155)
[2018-10-28 16:24:16,594] INFO Processing 1 records from Kafka. (com.yb.connect.sink.YBSinkTask:419)
[2018-10-28 16:24:16,612] INFO Prepare SinkRecord ...
[2018-10-28 16:24:16,618] INFO Bind timestamp of type timestamp (com.yb.connect.sink.YBSinkTask:204)
...
```

8. Confirm that the rows are in the table using cqlsh.
```cqlsh> select * from iotdemo.ingress limit 5;

 vehicleid                            | routeid  | vehicletype | longitude  | latitude  | timestamp                       | speed | fuellevel
--------------------------------------+----------+-------------+------------+-----------+---------------------------------+-------+-----------
 cc83f207-f233-49f3-8474-3b5f88379b93 | Route-43 | Small Truck | -97.978294 | 35.816948 | 2018-10-28 16:24:51.000000+0000 |    45 |        37
 0cb3908c-34a2-4642-8a22-9c330030d0d3 | Route-43 | Large Truck |  -97.32471 | 35.677494 | 2018-10-28 16:24:51.000000+0000 |    68 |        35
 6c6083c6-d05d-48a9-8119-10d7b348272d | Route-82 |  18 Wheeler | -96.268425 | 34.652107 | 2018-10-28 16:24:51.000000+0000 |    75 |        13
 d7f2f71a-4347-46a3-aa28-c4048328e7f5 | Route-82 | Large Truck |   -96.2321 | 34.241295 | 2018-10-28 16:24:51.000000+0000 |    77 |        37
 7af07ac6-0902-42ee-ad1c-657e96473dac | Route-37 | Small Truck |   -95.0934 |  33.29403 | 2018-10-28 15:17:55.000000+0000 |    89 |        22

(5 rows)
cqlsh> select count(*) from iotdemo.ingress;
...
```

## Future Work
- Add more data types.
- Add more tests.
- Add restartability.
- Add YugaByte DB as a Connect Source.
- Update README!

## License
This software is distributed under an Apache 2.0 license. See the [LICENSE.txt](https://github.com/YugaByte/yb-kafka-connector/LICENSE) file for details.
