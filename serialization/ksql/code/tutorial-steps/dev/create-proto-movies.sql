CREATE STREAM movies_proto
    WITH (KAFKA_TOPIC='proto-movies', VALUE_FORMAT='protobuf') AS
    SELECT * FROM movies_avro;
