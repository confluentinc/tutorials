docker exec broker kafka-console-consumer --topic example-topic --bootstrap-server broker:9092 \
 --property print.key=true \
 --property key.separator="-" \
 --partition 1 \
 --offset 6 \
 --max-messages 3
