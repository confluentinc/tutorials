docker exec -i schema-registry /usr/bin/kafka-avro-console-producer --topic ratings --bootstrap-server broker:9092 --property value.schema="$(< src/main/avro/rating.avsc)"
