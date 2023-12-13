curl -X PUT http://localhost:8083/connectors/pizza_orders/config \
     -i -H "Content-Type: application/json" -d '{
    "connector.class"                             : "io.confluent.kafka.connect.datagen.DatagenConnector",
    "quickstart"                                  : "pizza_orders",
    "schema.keyfield"                             : "store_id",
    "kafka.topic"                                 : "pizza_orders",
    "key.converter"                               : "org.apache.kafka.connect.converters.IntegerConverter",
    "value.converter"                             : "io.confluent.connect.avro.AvroConverter",
    "value.converter.schema.registry.url"         : "http://schema-registry:8081",
    "value.converter.schemas.enable"              : "false",
    "max.interval"                                : 1000,
    "tasks.max"                                   : "1"
    
}'

