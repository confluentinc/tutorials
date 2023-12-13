CREATE TABLE repair_center_tab (repair_state VARCHAR PRIMARY KEY, long DOUBLE, lat DOUBLE)
       WITH (kafka_topic='repair_center', value_format='avro', partitions=1);

CREATE STREAM insurance_event_stream (customer_name VARCHAR, phone_model VARCHAR, event VARCHAR,
                                      state VARCHAR, long DOUBLE, lat DOUBLE)
       WITH (kafka_topic='phone_event_raw', value_format='avro', partitions=1);

CREATE STREAM insurance_event_with_repair_info AS
SELECT * FROM insurance_event_stream iev
INNER JOIN repair_center_tab rct ON iev.state = rct.repair_state;

CREATE STREAM insurance_event_dist AS
SELECT iev_customer_name, iev_state,
              geo_distance(iev_lat, iev_long, rct_lat, rct_long, 'km') AS dist_to_repairer_km
FROM insurance_event_with_repair_info;