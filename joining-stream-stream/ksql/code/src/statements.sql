CREATE STREAM orders (ID INT KEY, order_ts VARCHAR, total_amount DOUBLE, customer_name VARCHAR)
    WITH (KAFKA_TOPIC='_orders',
          VALUE_FORMAT='AVRO',
          TIMESTAMP='order_ts',
          TIMESTAMP_FORMAT='yyyy-MM-dd''T''HH:mm:ssX',
          PARTITIONS=4);

CREATE STREAM shipments (ID VARCHAR KEY, ship_ts VARCHAR, order_id INT, warehouse VARCHAR)
    WITH (KAFKA_TOPIC='_shipments',
          VALUE_FORMAT='AVRO',
          TIMESTAMP='ship_ts',
          TIMESTAMP_FORMAT='yyyy-MM-dd''T''HH:mm:ssX',
          PARTITIONS=4);

CREATE STREAM SHIPPED_ORDERS AS
    SELECT O.ID AS ORDER_ID,
           TIMESTAMPTOSTRING(O.ROWTIME, 'yyyy-MM-dd HH:mm:ss', 'UTC') AS ORDER_TS,
           O.TOTAL_AMOUNT,
           O.CUSTOMER_NAME,
           S.ID AS SHIPMENT_ID,
           TIMESTAMPTOSTRING(S.ROWTIME, 'yyyy-MM-dd HH:mm:ss', 'UTC') AS SHIPMENT_TS,
           S.WAREHOUSE,
           (S.ROWTIME - O.ROWTIME) / 1000 / 60 AS SHIP_TIME
    FROM ORDERS O INNER JOIN SHIPMENTS S
    WITHIN 7 DAYS
    ON O.ID = S.ORDER_ID;
