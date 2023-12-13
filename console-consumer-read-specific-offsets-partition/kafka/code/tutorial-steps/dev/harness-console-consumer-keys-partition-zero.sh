docker exec broker kafka-console-consumer --topic example-topic --bootstrap-server broker:9092 \
 --from-beginning \
 --property print.key=true \
 --property key.separator="-" \
 --partition 0 \
 --max-messages 3
