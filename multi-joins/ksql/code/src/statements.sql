CREATE TABLE customers (customerid STRING PRIMARY KEY, customername STRING)
    WITH (KAFKA_TOPIC='customers',
          VALUE_FORMAT='json',
          PARTITIONS=1);

CREATE TABLE items (itemid STRING PRIMARY KEY, itemname STRING)
    WITH (KAFKA_TOPIC='items',
          VALUE_FORMAT='json',
          PARTITIONS=1);

CREATE STREAM orders (orderid STRING KEY, customerid STRING, itemid STRING, purchasedate STRING)
    WITH (KAFKA_TOPIC='orders',
          VALUE_FORMAT='json',
          PARTITIONS=1);

CREATE STREAM orders_enriched AS 
  SELECT customers.customerid AS customerid, customers.customername AS customername, 
         orders.orderid, orders.purchasedate,
         items.itemid, items.itemname
  FROM orders 
  LEFT JOIN customers on orders.customerid = customers.customerid 
  LEFT JOIN items on orders.itemid = items.itemid;
