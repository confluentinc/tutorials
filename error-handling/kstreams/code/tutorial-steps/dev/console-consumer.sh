docker exec -t broker kafka-console-consumer \
 --bootstrap-server broker:9092 \
 --topic output-topic \
 --from-beginning \
 --max-messages 6

