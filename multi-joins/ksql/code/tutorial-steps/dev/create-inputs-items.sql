CREATE TABLE items (itemid STRING PRIMARY KEY, itemname STRING) 
    WITH (KAFKA_TOPIC='items', 
          VALUE_FORMAT='json', 
          PARTITIONS=1);
