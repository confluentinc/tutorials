CREATE STREAM raw_quotes(ticker varchar key, bid int, ask int, bidqty int, askqty int)
    WITH (kafka_topic='stockquotes', value_format='avro', partitions=1);