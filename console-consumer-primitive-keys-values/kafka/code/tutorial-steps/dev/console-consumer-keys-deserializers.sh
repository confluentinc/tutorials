kafka-console-consumer --topic example --bootstrap-server broker:9092 \
 --from-beginning \
 --property print.key=true \
 --property key.separator=" : " \
 --max-messages 10 \
 --key-deserializer "org.apache.kafka.common.serialization.LongDeserializer" \
 --value-deserializer "org.apache.kafka.common.serialization.DoubleDeserializer"