CREATE STREAM george_martin WITH (kafka_topic = 'george_martin_books', partitions = 1) AS
    SELECT author, title
    FROM all_publications
    WHERE author = 'George R. R. Martin';
