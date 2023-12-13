CREATE STREAM purchases_pii_removed
    WITH (kafka_topic='purchases_pii_removed', value_format='json', partitions=1) AS
    SELECT ORDER_ID, PRODUCT, ORDER_TOTAL_USD, TOWN, COUNTRY
    FROM PURCHASES;
