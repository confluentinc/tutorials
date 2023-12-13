SET 'auto.offset.reset' = 'earliest';

-- Register the initial stream
CREATE STREAM rabbit (userid VARCHAR,
                      timestamp BIGINT,
                      transaction VARCHAR,
                      amount VARCHAR
) WITH (
  KAFKA_TOPIC = 'from-rabbit',
  VALUE_FORMAT = 'json',
  PARTITIONS = 6
);

-- Convert the stream to typed fields
CREATE STREAM rabbit_transactions 
  WITH (KAFKA_TOPIC = 'rabbit_transactions') AS
  SELECT TRANSACTION AS TX_TYPE,
         TIMESTAMP AS TX_TIMESTAMP,
         SUBSTRING(AMOUNT,1,1) AS CURRENCY,
         CAST(SUBSTRING(AMOUNT,2,LEN(AMOUNT)-1) AS DECIMAL(9,2)) AS TX_AMOUNT,
         CAST(USERID AS INT) AS USERID
  FROM rabbit
  WHERE TIMESTAMP IS NOT NULL
  EMIT CHANGES;

-- Count the number of transactions
CREATE TABLE number_transactions
  WITH (KAFKA_TOPIC = 'number_transactions') AS
  SELECT USERID,
         COUNT(USERID)
  FROM rabbit_transactions
  GROUP BY USERID
  EMIT CHANGES;
