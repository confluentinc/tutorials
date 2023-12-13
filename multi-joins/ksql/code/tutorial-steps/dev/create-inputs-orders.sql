CREATE STREAM orders (orderid STRING KEY, customerid STRING, itemid STRING, purchasedate STRING) 
    WITH (KAFKA_TOPIC='orders', 
          VALUE_FORMAT='json',
          PARTITIONS=1); 
