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


CREATE STREAM SUMMARY_RESULTS AS
  SELECT FIRST_NAME + ' ' + LAST_NAME +
       ' purchased ' +
       CAST(NUM_SHARES AS VARCHAR) +
       ' shares of ' +
       SYMBOL AS SUMMARY
FROM ACTIVITY_STREAM;