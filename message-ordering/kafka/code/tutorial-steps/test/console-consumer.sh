docker exec -t broker kafka-console-consumer --topic myTopic \
 --bootstrap-server broker:9092 \
 --from-beginning \
 --property print.key=true \
 --property key.separator=" : "
