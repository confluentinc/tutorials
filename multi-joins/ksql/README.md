# Multi-join expressions

In this tutorial, we demonstrate how to join multiple streams and tables together using an example from retail sales.


## Setup

For this example, let's say you have 2 tables `customers` and `items` and a stream `orders` and you want to do a join between all three to enrich the `orders` stream with more complete information.

Here are the table definitions:
   
```sql
CREATE TABLE customers (customerid STRING PRIMARY KEY, customername STRING)
    WITH (KAFKA_TOPIC='customers',
          VALUE_FORMAT='json',
          PARTITIONS=1);
```

```sql
CREATE TABLE items (itemid STRING PRIMARY KEY, itemname STRING)
    WITH (KAFKA_TOPIC='items',
          VALUE_FORMAT='json',
          PARTITIONS=1);
```

And here is the stream definition:
```sql
CREATE STREAM orders (orderid STRING KEY, customerid STRING, itemid STRING, purchasedate STRING)
    WITH (KAFKA_TOPIC='orders',
          VALUE_FORMAT='json',
          PARTITIONS=1);
```

Now, to create an enriched order stream, you'll have an SQL statement like this:
```sql
CREATE STREAM orders_enriched AS
  SELECT customers.customerid AS customerid, customers.customername AS customername,
         orders.orderid, orders.purchasedate,
         items.itemid, items.itemname
  FROM orders
  LEFT JOIN customers on orders.customerid = customers.customerid
  LEFT JOIN items on orders.itemid = items.itemid;
```
