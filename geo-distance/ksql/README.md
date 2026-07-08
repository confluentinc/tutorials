<!-- title: How to calculate the geographic distance between two points with ksqlDB -->
<!-- description: In this tutorial, learn how to calculate the geographic distance between two points with ksqlDB, with step-by-step instructions and supporting code. -->

# How to calculate the geographic distance between two points with ksqlDB

You can use the ksqlDB [`GEO_DISTANCE`](https://docs.ksqldb.io/en/latest/developer-guide/ksqldb-reference/scalar-functions/#GEO_DISTANCE) function to calculate the distance between two latitude and longitude points.

## Setup

You have a topic that contains longitude and latitude data.  For our example, let's say it contains information for phone repair shops:

```sql
CREATE TABLE repair_center_table (repair_state VARCHAR PRIMARY KEY, long DOUBLE, lat DOUBLE)
       WITH (KAFKA_TOPIC='repair_center',
             PARTITIONS=1,
             VALUE_FORMAT='AVRO');
```

Now you also have a topic that contains insurance claim event data for people who have lost or damaged an insured phone:

```sql
CREATE STREAM insurance_event_stream (customer_name VARCHAR, phone_model VARCHAR, event VARCHAR,
                                      state VARCHAR, long DOUBLE, lat DOUBLE)
       WITH (KAFKA_TOPIC='phone_event_raw',
             PARTITIONS=1,
             VALUE_FORMAT='AVRO');
```

The goal is to route customers to the nearest repair shop.
In order to calculate how far away the repair center is from the insurance event, we will need to create a stream that joins the insurance events with our repair center reference data. or this use case, let’s assume there is only one repair center in each `STATE` and the repair center in an event’s `STATE` is the closest repair center.
 
```sql
CREATE STREAM insurance_event_with_repair_info AS
SELECT * FROM insurance_event_stream iev
INNER JOIN repair_center_table rct ON iev.state = rct.repair_state
EMIT CHANGES;
```

Now you have a stream that contains the lat-long of the repair shop and the phone damage event lat-long. 

The last step is to convert the two sets of lat-long coordinates to a distance:

```sql
SELECT iev_customer_name,
    iev_state,
    GEO_DISTANCE(iev_lat, iev_long, rct_lat, rct_long, 'miles') AS dist_to_repairer_km
FROM insurance_event_with_repair_info
EMIT CHANGES;
```

The `GEO_DISTANCE` function calculates the great-circle distance between two lat-long points, both specified in decimal degrees. An optional final parameter specifies km (the default) or miles.

## Running the example

### Prerequisites

* Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
* [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.

### Run the commands

Clone the `confluentinc/tutorials` GitHub repository (if you haven't already) and navigate to the `tutorials` directory:

```shell
git clone git@github.com:confluentinc/tutorials.git
cd tutorials
```

Start ksqlDB and Kafka:

```shell
docker compose -f ./docker/docker-compose-ksqldb.yml up -d
```

Next, open the ksqlDB CLI:

```shell
docker exec -it ksqldb-cli ksql http://ksqldb-server:8088
```

Run the following SQL statements to create the `repair_center_table` table and `insurance_event_stream` stream backed
by Kafka running in Docker and populate them with test data.

```sql
CREATE TABLE repair_center_table (repair_state VARCHAR PRIMARY KEY, long DOUBLE, lat DOUBLE)
       WITH (KAFKA_TOPIC='repair_center',
             PARTITIONS=1,
             VALUE_FORMAT='AVRO');
```

```sql
INSERT INTO repair_center_table (repair_state, long, lat) VALUES ('NSW', 151.1169, -33.863);
INSERT INTO repair_center_table (repair_state, long, lat) VALUES ('VIC', 145.1549, -37.9389);
```

```sql
CREATE STREAM insurance_event_stream (customer_name VARCHAR, phone_model VARCHAR, event VARCHAR,
                                      state VARCHAR, long DOUBLE, lat DOUBLE)
       WITH (KAFKA_TOPIC='phone_event_raw',
             PARTITIONS=1,
             VALUE_FORMAT='AVRO');
```

```sql
INSERT INTO insurance_event_stream (customer_name, phone_model, event, state, long, lat)
    VALUES ('Lindsey', 'iPhone 11 Pro', 'dropped', 'NSW', 151.25664, -33.85995);
INSERT INTO insurance_event_stream (customer_name, phone_model, event, state, long, lat)
    VALUES ('Debbie', 'Samsung Note 20', 'water', 'NSW', 151.24504, -33.89640);
```

Next, create a stream that joins the insurance events with our repair center reference data. Note that we first tell
ksqlDB to consume from the beginning of the `insurance_event_stream` stream.
 
```sql
SET 'auto.offset.reset'='earliest';
  
CREATE STREAM insurance_event_with_repair_info AS
  SELECT * FROM insurance_event_stream iev
  INNER JOIN repair_center_table rct
  ON iev.state = rct.repair_state
  EMIT CHANGES;
```

Finally, convert the two sets of lat-long coordinates to a distance:

```sql
SELECT iev_customer_name,
    iev_state,
    GEO_DISTANCE(iev_lat, iev_long, rct_lat, rct_long, 'miles') AS dist_to_repairer_km
FROM insurance_event_with_repair_info
EMIT CHANGES;
```

The query output should look like this:

```plaintext
+-----------------------------+-----------------------------+-----------------------------+
|IEV_CUSTOMER_NAME            |IEV_STATE                    |DIST_TO_REPAIRER_KM          |
+-----------------------------+-----------------------------+-----------------------------+
|Lindsey                      |NSW                          |8.020734621148486            |
|Debbie                       |NSW                          |7.704588172240076            |
Query Completed
```

When you are finished, exit the ksqlDB CLI by entering `CTRL-D` and clean up the containers used for this tutorial by running:

```shell
docker compose -f ./docker/docker-compose-ksqldb.yml down
```
