CREATE STREAM actingevents (name VARCHAR, title VARCHAR, genre VARCHAR) 
    WITH (KAFKA_TOPIC = 'acting-events', PARTITIONS = 1, VALUE_FORMAT = 'AVRO');