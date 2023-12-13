confluent kafka topic produce raw-movies \
      --value-format avro \
      --schema src/main/avro/input_movie_event.avsc