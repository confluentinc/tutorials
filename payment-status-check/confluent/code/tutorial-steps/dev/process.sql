SET 'auto.offset.reset' = 'earliest';

-- Register the initial streams and tables from the Kafka topics
CREATE STREAM PAYMENTS (
  PAYMENT_ID INTEGER KEY,
  CUSTID INTEGER,
  ACCOUNTID INTEGER,
  AMOUNT INTEGER,
  BANK VARCHAR
) WITH (
  KAFKA_TOPIC = 'payments',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 6
);

CREATE STREAM aml_status (
  PAYMENT_ID INTEGER,
  BANK VARCHAR,
  STATUS VARCHAR
) WITH (
  KAFKA_TOPIC = 'aml_status',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 6
);

CREATE STREAM funds_status (
  PAYMENT_ID INTEGER,
  REASON_CODE VARCHAR,
  STATUS VARCHAR
) WITH (
  KAFKA_TOPIC = 'funds_status',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 6
);

CREATE TABLE customers (
  ID INTEGER PRIMARY KEY, 
  FIRST_NAME VARCHAR, 
  LAST_NAME VARCHAR, 
  EMAIL VARCHAR, 
  GENDER VARCHAR, 
  STATUS360 VARCHAR
) WITH (
  KAFKA_TOPIC = 'customer_info',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 6
);

-- Enrich Payments stream with Customers table
CREATE STREAM enriched_payments AS SELECT
  p.payment_id AS payment_id,
  p.custid AS customer_id,
  p.accountid,
  p.amount,
  p.bank,
  c.first_name,
  c.last_name,
  c.email,
  c.status360
  FROM payments p LEFT JOIN customers c ON p.custid = c.id;

-- Combine the status streams
CREATE STREAM payment_statuses AS SELECT
  payment_id,
  status,
  'AML' AS source_system
  FROM aml_status;

INSERT INTO payment_statuses SELECT payment_id, status, 'FUNDS' AS source_system FROM funds_status;

-- Combine payment and status events in 1 hour window. 
CREATE STREAM payments_with_status AS SELECT
  ep.payment_id AS payment_id,
  ep.accountid,
  ep.amount,
  ep.bank,
  ep.first_name,
  ep.last_name,
  ep.email,
  ep.status360,
  ps.status,
  ps.source_system
  FROM enriched_payments ep LEFT JOIN payment_statuses ps WITHIN 1 HOUR ON ep.payment_id = ps.payment_id ;

-- Aggregate data to the final table
CREATE TABLE payments_final AS SELECT
  payment_id,
  HISTOGRAM(status) AS status_counts,
  COLLECT_LIST('{ "system" : "' + source_system + '", "status" : "' + STATUS + '"}') AS service_status_list
  FROM payments_with_status
  WHERE status IS NOT NULL
  GROUP BY payment_id;
