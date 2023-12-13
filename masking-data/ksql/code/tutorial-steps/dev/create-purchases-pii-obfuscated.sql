CREATE STREAM purchases_pii_obfuscated
    WITH (kafka_topic='purchases_pii_obfuscated', value_format='json', partitions=1) AS
    SELECT MASK(CUSTOMER_NAME) AS CUSTOMER_NAME,
           MASK(DATE_OF_BIRTH) AS DATE_OF_BIRTH,
           ORDER_ID, PRODUCT, ORDER_TOTAL_USD, TOWN, COUNTRY
    FROM PURCHASES;
