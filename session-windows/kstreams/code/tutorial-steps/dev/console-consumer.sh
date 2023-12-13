docker exec -t broker kafka-console-consumer \
 --bootstrap-server broker:9092 \
 --topic output-topic \
 --property print.key=true \
 --property key.separator=" : "  \
 --from-beginning \
 --max-messages 4

