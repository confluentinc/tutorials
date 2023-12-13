CREATE STREAM ACTIVITY_STREAM (
        	      ID VARCHAR,
                      NUM_SHARES INT,
                      AMOUNT DOUBLE,
                      TXN_TS VARCHAR,
                      FIRST_NAME VARCHAR,
                      LAST_NAME  VARCHAR,
                      SYMBOL VARCHAR )

 WITH (KAFKA_TOPIC='stock_purchases',
       VALUE_FORMAT='JSON',
       PARTITIONS=1);
 