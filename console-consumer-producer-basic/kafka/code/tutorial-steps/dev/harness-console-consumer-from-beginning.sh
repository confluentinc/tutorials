docker exec -t broker kafka-console-consumer \
  --topic orders \
  --bootstrap-server broker:9092 \
  --from-beginning \
  --max-messages 8
