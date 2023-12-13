curl -i -X PUT http://localhost:8083/connectors/jdbc_source_postgres_01/config \
     -H "Content-Type: application/json" \
     -d '{
            "connector.class": "io.confluent.connect.jdbc.JdbcSourceConnector",
            "connection.url": "jdbc:postgresql://postgres:5432/postgres",
            "connection.user": "postgres",
            "connection.password": "postgres",
            "mode":"incrementing",
            "incrementing.column.name":"city_id",
            "topic.prefix":"postgres_",
            "transforms":"copyFieldToKey,extractKeyFromStruct",
            "transforms.copyFieldToKey.type":"org.apache.kafka.connect.transforms.ValueToKey",
            "transforms.copyFieldToKey.fields":"city_id",
            "transforms.extractKeyFromStruct.type":"org.apache.kafka.connect.transforms.ExtractField$Key",
            "transforms.extractKeyFromStruct.field":"city_id"
        }'
