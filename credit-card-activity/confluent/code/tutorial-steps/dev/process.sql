SET 'auto.offset.reset' = 'earliest';

-- Create the stream of customer data
CREATE STREAM fd_cust_raw_stream (
  ID BIGINT, 
  FIRST_NAME VARCHAR, 
  LAST_NAME VARCHAR, 
  EMAIL VARCHAR, 
  AVG_CREDIT_SPEND DOUBLE
) WITH (
  KAFKA_TOPIC = 'FD_customers',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 6
);

-- Repartition the customer data stream by account_id to prepare for the join later
CREATE STREAM fd_customer_rekeyed WITH (KAFKA_TOPIC = 'fd_customer_rekeyed') AS
  SELECT * 
  FROM fd_cust_raw_stream 
  PARTITION BY ID;

-- Register the partitioned customer data topic as a table
CREATE TABLE fd_customers (
  ID BIGINT PRIMARY KEY,
  FIRST_NAME VARCHAR, 
  LAST_NAME VARCHAR, 
  EMAIL VARCHAR, 
  AVG_CREDIT_SPEND DOUBLE
) WITH (
  KAFKA_TOPIC = 'fd_customer_rekeyed',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 6
);

-- Create the stream of transactions
CREATE STREAM fd_transactions (
  ACCOUNT_ID BIGINT,
  TIMESTAMP VARCHAR,
  CARD_TYPE VARCHAR,
  AMOUNT DOUBLE,
  IP_ADDRESS VARCHAR,
  TRANSACTION_ID VARCHAR
) WITH (
  KAFKA_TOPIC = 'FD_transactions',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 6
);

-- Join the transactions to customer information
CREATE STREAM fd_transactions_enriched WITH (KAFKA_TOPIC = 'transactions_enriched') AS
  SELECT
    T.ACCOUNT_ID,
    T.CARD_TYPE,
    T.AMOUNT, 
    C.FIRST_NAME + ' ' + C.LAST_NAME AS FULL_NAME,
    C.AVG_CREDIT_SPEND
  FROM fd_transactions T
  INNER JOIN fd_customers C
  ON T.ACCOUNT_ID = C.ID;

-- Aggregate the stream of transactions for each account ID using a two-hour
-- tumbling window, and filter for accounts in which the total spend in a
-- two-hour period is greater than the customer’s average:
CREATE TABLE fd_possible_stolen_card WITH (KAFKA_TOPIC = 'FD_possible_stolen_card', KEY_FORMAT = 'JSON') AS
  SELECT
    TIMESTAMPTOSTRING(WINDOWSTART, 'yyyy-MM-dd HH:mm:ss Z') AS WINDOW_START,
    ACCOUNT_ID,
    CARD_TYPE,
    SUM(AMOUNT) AS TOTAL_CREDIT_SPEND,
    FULL_NAME,
    MAX(AVG_CREDIT_SPEND) AS AVG_CREDIT_SPEND
  FROM fd_transactions_enriched
  WINDOW TUMBLING (SIZE 2 HOURS)
  GROUP BY ACCOUNT_ID, CARD_TYPE, FULL_NAME
  HAVING SUM(AMOUNT) > MAX(AVG_CREDIT_SPEND);
