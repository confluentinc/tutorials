kafka-avro-console-producer \
  --topic orders-avro \
  --bootstrap-server broker:9092 \
  --property schema.registry.url=http://localhost:8081 \
  --property value.schema="$(< /etc/tutorial/orders-avro-schema.json)" \
  --property key.serializer=org.apache.kafka.common.serialization.StringSerializer \
  --property parse.key=true \
  --property key.separator=":"
