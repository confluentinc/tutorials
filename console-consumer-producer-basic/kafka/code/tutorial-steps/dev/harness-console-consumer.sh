docker exec -t broker kafka-console-consumer \
  --topic orders \
  --bootstrap-server broker:9092 \
  --max-messages 4
