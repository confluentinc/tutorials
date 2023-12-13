docker exec -it broker /usr/bin/kafka-console-consumer \
 --topic output-topic \
 --bootstrap-server broker:9092 \
 --from-beginning \
 --property print.key=true \
 --property key.separator=" : " \
 --max-messages 5
