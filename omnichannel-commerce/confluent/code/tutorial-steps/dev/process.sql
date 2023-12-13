SET 'auto.offset.reset' = 'earliest';

-- Create a stream of products (shoes)
CREATE STREAM shoes (
  id VARCHAR,
  brand VARCHAR,
  name VARCHAR,
  sale_price INT,
  rating DOUBLE,
  ts BIGINT
) WITH (
  KAFKA_TOPIC = 'shoes',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 1
);

-- Create a stream of customers
CREATE STREAM shoe_customers (
  id VARCHAR,
  first_name VARCHAR,
  last_name VARCHAR,
  email VARCHAR,
  phone VARCHAR,
  street_address VARCHAR,
  state VARCHAR,
  zip_code VARCHAR,
  country VARCHAR,
  country_code VARCHAR
) WITH (
  KAFKA_TOPIC = 'shoe_customers',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 1
);

-- Create a stream of orders
CREATE STREAM shoe_orders (
  order_id INT,
  product_id VARCHAR,
  customer_id VARCHAR,
  ts BIGINT
) WITH (
  KAFKA_TOPIC = 'shoe_orders',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 1,
  TIMESTAMP = 'ts'
);

-- Create a stream of product website clicks
CREATE STREAM shoe_clickstream (
  product_id VARCHAR,
  user_id VARCHAR,
  view_time INT,
  page_url VARCHAR,
  ip VARCHAR,
  ts BIGINT
) WITH (
  KAFKA_TOPIC = 'shoe_clickstream',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 1,
  TIMESTAMP = 'ts'
);

-- Create a stream of enriched orders
CREATE STREAM shoe_orders_enriched WITH (
  kafka_topic='shoe_orders_enriched',
  partitions=1,
  value_format='JSON'
) AS
SELECT * FROM shoe_orders
  INNER JOIN shoe_clickstream
    WITHIN 1 HOUR
    GRACE PERIOD 1 MINUTE
    ON shoe_orders.customer_id = shoe_clickstream.user_id
EMIT CHANGES;
