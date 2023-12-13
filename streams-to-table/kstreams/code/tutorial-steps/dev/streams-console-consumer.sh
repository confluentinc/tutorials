kafka-console-consumer --topic streams-output-topic --bootstrap-server broker:9092 \
--from-beginning \
--property print.key=true \
--property key.separator=" - "