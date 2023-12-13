kafka-avro-console-producer \
  --topic orders-avro \
  --bootstrap-server broker:9092 \
  --property schema.registry.url=http://localhost:8081 \
  --property value.schema="$(< /etc/tutorial/orders-avro-schema.json)"
