confluent kafka topic produce clicks \
  --parse-key \
  --value-format avro \
  --schema src/main/avro/click.avsc