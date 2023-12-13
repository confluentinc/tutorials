SET 'auto.offset.reset' = 'earliest';

-- create stream of locations
CREATE STREAM locations (
  vehicle_id INT,
  latitude DOUBLE,
  longitude DOUBLE,
  timestamp VARCHAR
) WITH (
  KAFKA_TOPIC = 'locations',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 6
);

-- fleet lookup table
CREATE TABLE fleet (
  vehicle_id INT PRIMARY KEY,
  driver_id INT,
  license BIGINT
) WITH (
  KAFKA_TOPIC = 'descriptions',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 6
);

-- enrich fleet location stream with more fleet information
CREATE STREAM fleet_location_enhanced AS
  SELECT
    l.vehicle_id,
    latitude,
    longitude,
    timestamp,
    f.driver_id,
    f.license
  FROM locations l
  LEFT JOIN fleet f ON l.vehicle_id = f.vehicle_id;
