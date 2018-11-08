# Kafka Connect to YugaByte DB

This is the framework to persist Kafka topics in to [YugaByte DB](https://github.com/YugaByte/yugabyte-db) using the [Kafka Connect](https://docs.confluent.io/3.0.0/connect/intro.html) Sink mechanism.

## Prerequisites

For building and using this project, we requires following tools pre-installed on the system.
- JDK - 1.8+
- Maven - 3.3+
- Clone this repo into `~/yb-kafka/yb-kafka-connector/` directory.


## Steps to setup and run connect sink
1. Setup and start Kafka
   - Download the Apache Kafka tarball
     ```
     mkdir -p ~/yb-kafka
     cd ~/yb-kafka
     wget http://apache.cs.utah.edu/kafka/2.0.0/kafka_2.11-2.0.0.tgz
     tar -xzf kafka_2.11-2.0.0.tgz
     ```
     Any latest version can be chosen, this is just as a sample.
   - Start Zookeeper and Kafka server
     ```
     ~/yb-kafka/kafka_2.11-2.0.0/bin/zookeeper-server-start.sh config/zookeeper.properties &
     ~/yb-kafka/kafka_2.11-2.0.0/bin/kafka-server-start.sh config/server.properties &
     ```
   - Create a Kafka topic
     ```
     $ ~/yb-kafka/kafka_2.11-2.0.0/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic test
     ```
     This needs to be done only once.
   - Run the following to produce data in that topic
     ```
     $ ~/yb-kafka/kafka_2.11-2.0.0/bin/kafka-console-producer.sh --broker-list localhost:9092 --topic test_topic
     ```
     Just cut-paste the following lines at the prompt:
     ```
     {"key" : "A", "value" : 1, "ts" : 1541559411000}
     {"key" : "B", "value" : 2, "ts" : 1541559412000}
     {"key" : "C", "value" : 3, "ts" : 1541559413000}
     ```
     Feel free to Ctrl-C this process or switch to a different shell as more values can be added later as well to the same topic.

2. Install YugaByte DB and create the keyspace/table.
   - [Install YugaByte DB and start a local cluster](https://docs.yugabyte.com/quick-start/install/).
   - Create a keyspace and table by running the following command. You can find `cqlsh` in the `bin` sub-directory located inside the YugaByte installation folder.
     ```sh
     $> cqlsh
     cqlsh> CREATE KEYSPACE IF NOT EXISTS demo;
     cqlsh> CREATE TABLE demo.test_table (key text, value bigint, ts timestamp, PRIMARY KEY (key));
     ```

3. Setup and run the Kafka Connect Sink
   - Setup the required jars needed by connect
     ```
     cd ~/yb-kafka/yb-kafka-connector/
     mvn clean install -DskipTests
     cp  ~/yb-kafka/yb-kafka-connector/target/yb-kafka-connnector-1.0.0.jar ~/yb-kafka/kafka_2.11-2.0.0/libs/
     cd ~/yb-kafka/kafka_2.11-2.0.0/libs/
     wget http://central.maven.org/maven2/io/netty/netty-all/4.1.25.Final/netty-all-4.1.25.Final.jar
     wget http://central.maven.org/maven2/com/yugabyte/cassandra-driver-core/3.2.0-yb-18/cassandra-driver-core-3.2.0-yb-18.jar
     wget http://central.maven.org/maven2/com/codahale/metrics/metrics-core/3.0.1/metrics-core-3.0.1.jar
     ```

   - Finally run the connect sink in standalone mode:
     ```
     ~/yb-kafka/kafka_2.11-2.0.0/bin/connect-standalone.sh ~/yb-kafka/yb-kafka-connector/resources/examples/kafka.connect.properties ~/yb-kafka/yb-kafka-connector/resources/examples/yugabyte.sink.properties 
     ```

     *Note*:
       - Setting the `bootstrap.servers` to a remote host/ports in the `kafka.connect.properties` file can help connect to any accessible existing Kafka cluster.
       - The `keyspace` and `tablename` values in the `yugabyte.sink.properties` file should match the values in the cqlsh commands in step 5.
       - The `topics` value should match the topic name from producer in step 6.
       - Setting the `yugabyte.cql.contact.points` to a non-local list of host/ports will help connect to any remote accessible existing YugaByte DB cluster.

   - Check the console output (optional)
     You should see something like this (relevant lines from YBSinkTask.java) on the console:
     ```
     [2018-10-28 16:24:16,037] INFO Start with keyspace=demo, table=test_table (com.yb.connect.sink.YBSinkTask:79)
     [2018-10-28 16:24:16,054] INFO Connecting to nodes: /127.0.0.1:9042,/127.0.0.2:9042,/127.0.0.3:9042 (com.yb.connect.sink.YBSinkTask:189)
     [2018-10-28 16:24:16,517] INFO Connected to cluster: cluster1 (com.yb.connect.sink.YBSinkTask:155)
     [2018-10-28 16:24:16,594] INFO Processing 3 records from Kafka. (com.yb.connect.sink.YBSinkTask:95)
     [2018-10-28 16:24:16,602] INFO Insert INSERT INTO demo.test_table(key,ts,value) VALUES (?,?,?) (com.yb.connect.sink.YBSinkTask:439)
     [2018-10-28 16:24:16,612] INFO Prepare SinkRecord ...
     [2018-10-28 16:24:16,618] INFO Bind 'ts' of type timestamp (com.yb.connect.sink.YBSinkTask:255)
     ...
     ```

4. Confirm that the rows are in the target table in the YugaByte DB cluster, using cqlsh.
   ```sh
   cqlsh> select * from demo.test_table;
   key | value | ts
   ----+-------+---------------------------------
     A |     1 | 2018-11-07 02:56:51.000000+0000
     C |     3 | 2018-11-07 02:56:53.000000+0000
     B |     2 | 2018-11-07 02:56:52.000000+0000
   ```
   Note that the timestamp value gets printed as a human readable date format automatically.

## Future Work
- Add more data types.
- Add more tests.
- Add restartability.
- Add YugaByte DB as a Connect Source.

## License
This software is distributed under an Apache 2.0 license. See the [LICENSE.txt](https://github.com/YugaByte/yb-kafka-connector/LICENSE) file for details.
