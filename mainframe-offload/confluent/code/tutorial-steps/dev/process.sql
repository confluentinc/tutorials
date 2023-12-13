SET 'auto.offset.reset' = 'earliest';

-- Create stream of transactions from the Kafka topic
CREATE STREAM mq_transactions (
  dep_account_no VARCHAR,
  dep_balance_dollars BIGINT,
  dep_balance_cents BIGINT,
  timestamp BIGINT
) WITH (
  KAFKA_TOPIC = 'mq_transactions',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 6
);

-- Normalize the data and calculate timestamp deltas
CREATE STREAM mq_transactions_normalized WITH (KAFKA_TOPIC = 'mq_transactions_normalized')
  AS SELECT
    dep_account_no,
    CAST(dep_balance_dollars AS DECIMAL(10,2)) + CAST(dep_balance_cents AS DECIMAL(10,2))/CAST(100 as DECIMAL(10,2)) as balance,
    timestamp AS ts_stream,
    UNIX_TIMESTAMP() AS ts_cache,
    (UNIX_TIMESTAMP() - timestamp) AS ts_delta
FROM mq_transactions
PARTITION BY dep_account_no
EMIT CHANGES;

CREATE SOURCE TABLE mq_cache (
    dep_account_no VARCHAR PRIMARY KEY,
    balance BIGINT,
    ts_stream BIGINT,
    ts_cache BIGINT,
    ts_delta BIGINT
) WITH (
    KAFKA_TOPIC = 'mq_transactions_normalized',
    KEY_FORMAT = 'KAFKA',
    VALUE_FORMAT = 'JSON'
);
