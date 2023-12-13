CREATE STREAM all_publications (bookid BIGINT KEY, author VARCHAR, title VARCHAR)
    WITH (kafka_topic = 'publication_events', partitions = 1, value_format = 'avro');
