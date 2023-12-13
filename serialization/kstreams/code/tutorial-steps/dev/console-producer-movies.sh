docker exec -i schema-registry /usr/bin/kafka-avro-console-producer --topic avro-movies --bootstrap-server broker:9092 --property value.schema="$(< src/main/avro/movie.avsc)"
