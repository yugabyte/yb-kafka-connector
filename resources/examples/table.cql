DROP TABLE iotdemo.ingress;
DROP KEYSPACE iotdemo;

// Create keyspace
CREATE KEYSPACE IF NOT EXISTS iotdemo WITH replication = {'class':'SimpleStrategy', 'replication_factor':1};

// Create table
CREATE TABLE iotdemo.ingress (vehicleId text, routeId text, vehicleType text, longitude text, latitude text, timeStamp timestamp, speed double, fuelLevel double, PRIMARY KEY (vehicleId));
