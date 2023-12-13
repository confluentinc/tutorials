confluent kafka topic produce input \
  --parse-key \
  --value-format avro \
  --schema src/main/avro/order.avsc
