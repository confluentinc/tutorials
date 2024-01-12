CREATE SOURCE CONNECTOR IF NOT EXISTS JDBC_SOURCE_DB_CONNECTOR WITH (
    'connector.class'= 'io.confluent.connect.jdbc.JdbcSourceConnector',
    'connection.url'= '<DB URL>',
    'connection.user'= '<DB USERNAME>',
    'connection.password'= '<DB PASSWORD>',
    'mode'= 'incrementing',
    'incrementing.column.name'= 'city_id',
    'topic.prefix'= 'postgres_',
    'transforms'= 'copyFieldToKey,extractKeyFromStruct,removeKeyFromValue',
    'transforms.copyFieldToKey.type'= 'org.apache.kafka.connect.transforms.ValueToKey',
    'transforms.copyFieldToKey.fields'= 'city_id',
    'transforms.extractKeyFromStruct.type'= 'org.apache.kafka.connect.transforms.ExtractField$Key',
    'transforms.extractKeyFromStruct.field'= 'city_id',
    'transforms.removeKeyFromValue.type'= 'org.apache.kafka.connect.transforms.ReplaceField$Value',
    'transforms.removeKeyFromValue.blacklist'= 'city_id',
    'key.converter' = 'org.apache.kafka.connect.converters.IntegerConverter'
);


SHOW CONNECTORS;

CREATE TABLE CITIES (CITY_ID INT PRIMARY KEY) WITH (KAFKA_TOPIC='postgres_cities', VALUE_FORMAT='AVRO');

SELECT CITY_ID, NAME, STATE FROM CITIES EMIT CHANGES;
