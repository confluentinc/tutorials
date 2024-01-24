CREATE STREAM all_publications (bookid BIGINT KEY,
                                 author VARCHAR,
                                 title VARCHAR)
    WITH (kafka_topic = 'publication_events', value_format = 'JSON');

CREATE STREAM george_martin WITH (kafka_topic = 'george_martin_books') AS
     SELECT *
         FROM all_publications
         WHERE author = 'George R. R. Martin';