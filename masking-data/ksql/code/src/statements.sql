CREATE STREAM purchases (order_id INT, customer_name VARCHAR, date_of_birth VARCHAR,
                         product VARCHAR, order_total_usd DOUBLE, town VARCHAR, country VARCHAR)
    WITH (kafka_topic='purchases', value_format='json', partitions=1);

CREATE STREAM purchases_pii_removed
    WITH (kafka_topic='purchases_pii_removed', value_format='json', partitions=1) AS
    SELECT ORDER_ID, PRODUCT, ORDER_TOTAL_USD, TOWN, COUNTRY
    FROM PURCHASES;

CREATE STREAM purchases_pii_obfuscated
    WITH (kafka_topic='purchases_pii_obfuscated', value_format='json', partitions=1) AS
    SELECT MASK(CUSTOMER_NAME) AS CUSTOMER_NAME,
           MASK(DATE_OF_BIRTH) AS DATE_OF_BIRTH,
           ORDER_ID, PRODUCT, ORDER_TOTAL_USD, TOWN, COUNTRY
    FROM PURCHASES;
