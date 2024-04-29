<!-- title: How to calculate the geographic distance between two points with ksqlDB -->
<!-- description: In this tutorial, learn how to calculate the geographic distance between two points with ksqlDB, with step-by-step instructions and supporting code. -->

# How to calculate the geographic distance between two points with ksqlDB

You can use the ksqlDB [`geo_distance`](https://docs.ksqldb.io/en/latest/developer-guide/ksqldb-reference/scalar-functions/#geo_distance) function to calculate the distance between two latitude and longitude points.

## Setup

You have a topic that contains longitude and latitude data.  For our example let's say it contains information for phone repair shops:

```sql
CREATE TABLE repair_center_table (repair_state VARCHAR PRIMARY KEY, long DOUBLE, lat DOUBLE)
       WITH (kafka_topic='repair_center', value_format='avro', partitions=1);
```

Now you also have a topic that contains insurance claim event data for people who have lost or damaged an insured phone:

```sql
CREATE STREAM insurance_event_stream (customer_name VARCHAR, phone_model VARCHAR, event VARCHAR,
                                      state VARCHAR, long DOUBLE, lat DOUBLE)
       WITH (kafka_topic='phone_event_raw', value_format='avro', partitions=1);
```

The goal is to route customers to the nearest repair shop.
In order to calculate how far away the repair center is from the insurance event, we will need to create a stream that joins the insurance events with our repair center reference data. or this use case, let’s assume there is only one repair center in each `STATE` and the repair center in an event’s `STATE` is the closest repair center.
 
```sql
CREATE STREAM insurance_event_with_repair_info AS
SELECT * FROM insurance_event_stream iev
INNER JOIN repair_center_table rct ON iev.state = rct.repair_state;
```
Now you have a stream that contains the lat-long of the repair shop and the phone damage event lat-long. 

The last step is to create a stream converting the two sets of lat-long coordinates to a distance, so you can choose which repair shop is closest:
```sql
CREATE STREAM insurance_event_dist AS
SELECT iev_customer_name, iev_state,
              geo_distance(iev_lat, iev_long, rct_lat, rct_long, 'miles') AS dist_to_repairer_km
FROM insurance_event_with_repair_info;
```

The `geo_distance` function calculates the great-circle distance between two lat-long points, both specified in decimal degrees. An optional final parameter specifies km (the default) or miles.