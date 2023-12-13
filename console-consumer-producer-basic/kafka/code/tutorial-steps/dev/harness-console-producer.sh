docker exec -i broker kafka-console-producer \
  --topic orders \
  --bootstrap-server broker:9092
