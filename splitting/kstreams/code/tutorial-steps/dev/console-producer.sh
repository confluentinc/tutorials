docker exec -i schema-registry /usr/bin/kafka-avro-console-producer --topic acting-events --bootstrap-server broker:9092 --property value.schema="$(< src/main/avro/acting_event.avsc)"
