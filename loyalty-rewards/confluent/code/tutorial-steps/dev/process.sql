SET 'auto.offset.reset' = 'earliest';

CREATE STREAM users (
  user_id VARCHAR KEY,
  name VARCHAR
) WITH (
  KAFKA_TOPIC = 'USERS',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 6
);

CREATE STREAM products (
  product_id VARCHAR KEY,
  category VARCHAR,
  price DECIMAL(10,2)
) WITH (
  KAFKA_TOPIC = 'products',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 6
);

CREATE STREAM purchases (
  user_id VARCHAR KEY,
  product_id VARCHAR
) WITH (
  KAFKA_TOPIC = 'purchases',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 6
);

-- Summarize products.
CREATE TABLE all_products AS
  SELECT
    product_id,
    LATEST_BY_OFFSET(category) AS category,
    LATEST_BY_OFFSET(CAST(price AS DOUBLE)) AS price
  FROM products
  GROUP BY product_id;

-- Enrich purchases.
CREATE STREAM enriched_purchases AS
  SELECT
    purchases.user_id,
    purchases.product_id AS product_id,
    all_products.category,
    all_products.price
  FROM purchases
    LEFT JOIN all_products ON purchases.product_id = all_products.product_id;

CREATE TABLE sales_totals AS
  SELECT
    user_id,
    SUM(price) AS total,
    CASE
      WHEN SUM(price) > 400 THEN 'GOLD'
      WHEN SUM(price) > 300 THEN 'SILVER'
      WHEN SUM(price) > 200 THEN 'BRONZE'
      ELSE 'CLIMBING'
    END AS reward_level
  FROM enriched_purchases
  GROUP BY user_id;

CREATE TABLE caffeine_index AS
  SELECT
    user_id,
    COUNT(*) AS total,
    (COUNT(*) % 6) AS sequence,
    (COUNT(*) % 6) = 5 AS next_one_free
  FROM purchases
  WHERE product_id = 'coffee'
  GROUP BY user_id;

CREATE TABLE promotion_french_poodle
  AS
  SELECT
      user_id,
      collect_set(product_id) AS products,
      'french_poodle' AS promotion_name
  FROM purchases
  WHERE product_id IN ('dog', 'beret')
  GROUP BY user_id
  HAVING ARRAY_CONTAINS( collect_set(product_id), 'dog' )
  AND ARRAY_CONTAINS( collect_set(product_id), 'beret' )
  EMIT changes;

CREATE TABLE promotion_loose_leaf AS
  SELECT
      user_id,
      collect_set(product_id) AS products,
      'loose_leaf' AS promotion_name
  FROM enriched_purchases
  WHERE product_id IN ('coffee', 'tea')
  GROUP BY user_id
  HAVING ARRAY_CONTAINS( collect_set(product_id), 'coffee' )
  AND NOT ARRAY_CONTAINS( collect_set(product_id), 'tea' )
  AND sum(price) > 20;
