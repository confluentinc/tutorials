CREATE TABLE customers (customerid STRING PRIMARY KEY, customername STRING) 
    WITH (KAFKA_TOPIC='customers', 
          VALUE_FORMAT='json', 
          PARTITIONS=1);
