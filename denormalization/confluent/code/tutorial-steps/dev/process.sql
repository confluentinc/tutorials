SET 'auto.offset.reset' = 'earliest';

-- Create stream of sales_orders
CREATE STREAM sales_orders (
  order_id BIGINT,
  customer_id BIGINT,
  item VARCHAR,
  order_total_usd DECIMAL(10,2)
) WITH (
  KAFKA_TOPIC = 'sales_orders',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 6
);

-- Register the customer data topic as a table
CREATE TABLE customers (
  id BIGINT PRIMARY KEY,
  first_name VARCHAR,
  last_name VARCHAR,
  email VARCHAR
) WITH (
  KAFKA_TOPIC = 'customers',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 6
);

-- Denormalize data, joining facts (sales_orders) with the dimension (customer)
CREATE STREAM sales_orders_enriched WITH (
  KAFKA_TOPIC = 'sales_orders_enriched'
) AS
  SELECT
    c.id AS customer_id,
    o.order_id AS order_id,
    o.item AS item,
    o.order_total_usd AS order_total_usd,
    CONCAT(CONCAT(c.first_name , ' ') , c.last_name) AS full_name,
    c.email AS email
  FROM sales_orders o
    LEFT JOIN customers c
    ON o.customer_id = c.id;
