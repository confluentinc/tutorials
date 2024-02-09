# Joining two streams in ksqlDB

In this tutorial, we'll demonstrate how to join two event streams to create a new event stream.

## Setup

Consider you have two streams `orders` and `shipments`.
 
Here's the  `orders` stream:
```sql
CREATE STREAM orders (id INT KEY, order_ts VARCHAR, total_amount DOUBLE, customer_name VARCHAR)
    WITH (KAFKA_TOPIC='_orders',
          VALUE_FORMAT='JSON',
          TIMESTAMP='order_ts',
          TIMESTAMP_FORMAT='yyyy-MM-dd''T''HH:mm:ssX',
          PARTITIONS=4);
```

and the `shipments`:
```sql
CREATE STREAM shipments (id VARCHAR KEY, ship_ts VARCHAR, order_id INT, warehouse VARCHAR)
    WITH (KAFKA_TOPIC='_shipments',
          VALUE_FORMAT='JSON',
          TIMESTAMP='ship_ts',
          TIMESTAMP_FORMAT='yyyy-MM-dd''T''HH:mm:ssX',
          PARTITIONS=4);
```

You might have noticed that we specified 4 partitions for both streams. It’s not random that both streams have the same partition count. For joins to work correctly, the topics need to be co-partitioned, which is a fancy way of saying that all topics have the same number of partitions and are keyed the same way. 


Now you'll join these two streams to get more complete information on orders shipped within the last 7 days:

```sql
CREATE STREAM shipped_orders AS
    SELECT o.id AS order_id,
           TIMESTAMPTOSTRING(o.rowtime, 'yyyy-MM-dd HH:mm:ss', 'UTC') AS order_ts,
           o.total_amount,
           o.customer_name,
           s.id AS shipment_id,
           TIMESTAMPTOSTRING(s.rowtime, 'yyyy-MM-dd HH:mm:ss', 'UTC') AS shipment_ts,
           s.warehouse,
           (s.rowtime - o.rowtime) / 1000 / 60 AS ship_time
    FROM orders o INNER JOIN shipments s
    WITHIN 7 DAYS
    ON o.id = s.order_id;
```