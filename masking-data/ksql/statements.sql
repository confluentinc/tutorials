CREATE STREAM purchases (order_id INT, customer_name VARCHAR, date_of_birth VARCHAR,
                         product VARCHAR, order_total_usd DOUBLE, town VARCHAR, country VARCHAR)
    WITH (kafka_topic='purchases', value_format='json', partitions=1);

CREATE STREAM purchases_pii_obfuscated
    WITH (kafka_topic='purchases_pii_obfuscated', value_format='json', partitions=1) AS
    SELECT MASK(customer_name) AS CUSTOMER_NAME,
           MASK(date_of_birth) AS DATE_OF_BIRTH,
           order_id, product, order_total_usd, town, country
    FROM purchases;
