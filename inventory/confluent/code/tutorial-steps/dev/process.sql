SET 'auto.offset.reset' = 'earliest';

-- Create stream of inventory
CREATE STREAM inventory_stream (
  id VARCHAR KEY,
  item VARCHAR,
  quantity INTEGER
) WITH (
  VALUE_FORMAT = 'JSON',
  KAFKA_TOPIC = 'inventory',
  PARTITIONS = 6
);

-- Create stateful table with up-to-date information of inventory availability
CREATE TABLE inventory_stream_table
    WITH (KAFKA_TOPIC = 'inventory_table') AS
  SELECT
    item,
    SUM(quantity) AS item_quantity
  FROM inventory_stream
  GROUP BY item
  EMIT CHANGES;
