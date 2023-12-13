docker exec -t broker kafka-console-consumer --bootstrap-server localhost:9092 --topic myTopic --property print.key=true --property key.separator=, --partition 0 --from-beginning
