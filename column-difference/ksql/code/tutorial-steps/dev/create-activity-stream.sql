CREATE STREAM PURCHASE_STREAM (
        	      ID VARCHAR,
                      PREVIOUS_PURCHASE DOUBLE,
                      CURRENT_PURCHASE DOUBLE,
                      TXN_TS VARCHAR,
                      FIRST_NAME VARCHAR,
                      LAST_NAME  VARCHAR)

 WITH (KAFKA_TOPIC='customer_purchases',
       VALUE_FORMAT='JSON',
       PARTITIONS=1);
 