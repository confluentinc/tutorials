docker exec -i broker kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic myTopic \
  --property parse.key=true \
  --property key.separator=,
