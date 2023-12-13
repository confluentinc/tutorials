SET 'auto.offset.reset' = 'earliest';

-- Create stream of pages
CREATE STREAM pages (
  customer INTEGER,
  time BIGINT,
  page_id VARCHAR,
  page VARCHAR
) WITH (
  VALUE_FORMAT = 'JSON',
  KAFKA_TOPIC = 'pages',
  PARTITIONS = 6
);

-- Create stateful table with Array of pages visited by each customer, using the `COLLECT_LIST` function
-- Get `COUNT_DISTINCT` page IDs
CREATE TABLE pages_per_customer WITH (KAFKA_TOPIC = 'pages_per_customer') AS
SELECT
  customer,
  COLLECT_LIST(page) AS page_list,
  COUNT_DISTINCT (page_id) AS count_distinct
FROM pages
GROUP BY customer
EMIT CHANGES;
