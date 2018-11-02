# Kafka Connect to YugaByte DB

This is the framework to connect Kafka streams to use [YugaByte DB](https://github.com/YugaByte/yugabyte-db) as the data sink using the [Kafka Connect](https://docs.confluent.io/3.0.0/connect/intro.html) mechanism.

## Prerequisites

For building and using this project, we requires following tools pre-installed on the system.
- JDK - 1.8+
- Maven - 3.3+

## Steps to setup and run connect sink
1. Download Confluent Open Source from https://www.confluent.io/download/. This is a manual step, as Email id is needed (as of Nov 2018).
   Unbundle the content of the tar.gz to location `~/yb-kafka/confluent-os/confluent-5.0.0` using these steps.
   ```
   mkdir -p ~/yb-kafka/confluent-os
   cd ~/yb-kafka/confluent-os
   tar -xvf confluent-5.0.0-2.11.tar.gz
   ```

2. Include the YugaByte DB sink into the registered connectors:
  - Add `yb-sink=kafka-connect-yugabyte/kafka.sink.properties` to `~/yb-kafka/confluent-os/confluent-5.0.0/bin/confluent` file in this scope:
    ```
    declare -a connector_properties=(
    ...
    "yb-sink=kafka-connect-yugabyte/kafka.sink.properties"
    )
    ```

3. Include dependent jar into kafka connectors:
  - Build the jar from this repo and copy it for use by Kafka:
    ```
    cd  ~/yb-kafka/yb-kafka-connector/
    mvn clean install -DskipTests
    mkdir ~/yb-kafka/confluent-os/confluent-5.0.0/share/java/kafka-connect-yugabyte/
    cp  ~/yb-kafka/yb-kafka-connector/target/yb-kafka-connnector-1.0.0.jar ~/yb-kafka/confluent-os/confluent-5.0.0/share/java/kafka-connect-yugabyte/
    ```
  - Download the dependent jars from maven central repository using the following commands.
    ```
    cd ~/yb-kafka/confluent-os/confluent-5.0.0/share/java/kafka-connect-yugabyte/
    wget http://central.maven.org/maven2/io/netty/netty-all/4.1.25.Final/netty-all-4.1.25.Final.jar
    wget http://central.maven.org/maven2/com/yugabyte/cassandra-driver-core/3.2.0-yb-18/cassandra-driver-core-3.2.0-yb-18.jar
    wget http://central.maven.org/maven2/com/codahale/metrics/metrics-core/3.0.1/metrics-core-3.0.1.jar
    ```

    The final list of jars should look like this:
    ```
     $ ls -al
      -rw-r--r--@    85449 Oct 27  2013 metrics-core-3.0.1.jar
      -rw-r--r--@  3823147 Oct 27 15:18 netty-all-4.1.25.Final.jar
      -rw-r--r--   1100520 Oct 29 11:18 cassandra-driver-core-3.2.0-yb-18.jar
      -rw-r--r--     14934 Oct 29 11:19 yb-kafka-connnector-1.0.0.jar
     ```

4. Do the following to run Kafka and related components:
   ```
   export PATH=$PATH:~/yb-kafka/confluent-os/confluent-5.0.0/bin
   confluent start
   confluent stop connect
   confluent stop kafka-rest
   confluent status
   ```

   The output for the `confluent status` should look like
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
   - Create a keyspace and table by running the following command. You can find `cqlsh` in the `bin` sub-directory located inside the YugaByte installation folder.
   ```sh
   $> cqlsh
   cqlsh> CREATE KEYSPACE IF NOT EXISTS demo;
   cqlsh> CREATE TABLE demo.test_table (key text, value bigint, ts timestamp, PRIMARY KEY (key));
   ```

6. Kafka topic producer
   Run the following to produce data in a test topic
   ```
   $ ~/yb-kafka/confluent-os/confluent-5.0.0/bin/kafka-console-producer --broker-list localhost:9092 --topic test_topic
   ```
   Just cut-paste the following lines at the prompt:
   ```
   {"key" : "A", "value" : 1, "ts" : 20002000}
   {"key" : "B", "value" : 2, "ts" : 20002020}
   {"key" : "C", "value" : 3, "ts" : 20202020}
   ```
   Feel free to Ctrl-C this process or switch to a different shell as more values can be added later as well to the same topic.

7. Setup and run the Kafka Connect Sink
   - Setup the properties for kakfa server
     ```
     cp ~/yb-kafka/yb-kafka-connector/resources/examples/kafka.connect.properties ~/yb-kafka/confluent-os/confluent-5.0.0/etc/kafka/
     ```

     *Note*: Setting the `bootstrap.servers` to a remote host/ports in the same file can help connect to any accessible existing Kafka cluster.

   - Do the following to setup a yugabyte sink properties file
     ```
     mkdir -p ~/yb-kafka/confluent-os/confluent-5.0.0/etc/kafka-connect-yugabyte
     cp ~/yb-kafka/yb-kafka-connector/resources/examples/yugabyte.sink.properties ~/yb-kafka/confluent-os/confluent-5.0.0/etc/kafka-connect-yugabyte
     ```

     *Note*: The keyspace and tablename values in the kafka.connect.properties file should match the values in the cqlsh commands in step 5.
             The topics value should match the topic name from producer in step 6.
             Setting the `yugabyte.cql.contact.points` to a non-local list of host/ports will help connect to any remote accessible existing YugaByte DB cluster.

   - Finally run the connect sink in standalone mode:
     ```
     cd ~/yb-kafka/confluent-os/confluent-5.0.0
     ./bin/connect-standalone ./etc/kafka/kafka.connect.properties ./etc/kafka-connect-yugabyte/yugabyte.sink.properties
     ```

     You should see something like this (relevant lines from YBSinkTask.java) on the console:
     ```
     [2018-10-28 16:24:16,037] INFO Start with keyspace=demo, table=test_table (com.yb.connect.sink.YBSinkTask:69)
     [2018-10-28 16:24:16,054] INFO Connecting to nodes: /127.0.0.1:9042,/127.0.0.2:9042,/127.0.0.3:9042 (com.yb.connect.sink.YBSinkTask:149)
     [2018-10-28 16:24:16,517] INFO Connected to cluster: cluster1 (com.yb.connect.sink.YBSinkTask:155)
     [2018-10-28 16:24:16,594] INFO Processing 3 records from Kafka. (com.yb.connect.sink.YBSinkTask:419)
     [2018-10-28 16:24:16,602] INFO Insert INSERT INTO demo.test_table(key,ts,value) VALUES (?,?,?) (com.yb.connect.sink.YBSinkTask:417)
     [2018-10-28 16:24:16,612] INFO Prepare SinkRecord ...
     [2018-10-28 16:24:16,618] INFO Bind 'ts' of type timestamp (com.yb.connect.sink.YBSinkTask:204)
     ...
     ```

8. Confirm that the rows are in the table using cqlsh.
   ```
   cqlsh> select * from demo.test_table;

    key | value | ts
   -----+-------+---------------------------------
      A |     1 | 1970-01-01 05:33:22.000000+0000
      C |     3 | 1970-01-01 05:36:42.020000+0000
      B |     2 | 1970-01-01 05:33:22.020000+0000
   ```
   Note that the timestamp value gets printed as a human readable date format automatically.

## Future Work
- Add more data types.
- Add more tests.
- Add restartability.
- Add YugaByte DB as a Connect Source.
- Update README!

## License
This software is distributed under an Apache 2.0 license. See the [LICENSE.txt](https://github.com/YugaByte/yb-kafka-connector/LICENSE) file for details.
