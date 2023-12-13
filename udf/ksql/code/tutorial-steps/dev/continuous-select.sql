CREATE STREAM vwap WITH (kafka_topic = 'vwap', partitions = 1) AS
    SELECT ticker, 
           vwap(bid, bidqty, ask, askqty) AS vwap
    FROM raw_quotes
    EMIT CHANGES;
