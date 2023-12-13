CREATE STREAM TRANSACTION_STREAM (
        id VARCHAR,
              transaction STRUCT<num_shares INT,
                                amount DOUBLE,
                                txn_ts VARCHAR,
                                customer STRUCT<first_name VARCHAR, 
                                                last_name VARCHAR, 
                                                id INT, 
                                                email VARCHAR>,
                                   company STRUCT<name VARCHAR, 
                                                  ticker VARCHAR, 
                                                  id VARCHAR, 
                                                  address VARCHAR>>)     
 WITH (KAFKA_TOPIC='financial_txns',
       VALUE_FORMAT='JSON',
       PARTITIONS=1);


CREATE STREAM FINANCIAL_REPORTS AS
    SELECT
    TRANSACTION->num_shares AS SHARES,
    TRANSACTION->CUSTOMER->ID as CUST_ID,
    TRANSACTION->COMPANY->TICKER as SYMBOL
FROM
    TRANSACTION_STREAM;