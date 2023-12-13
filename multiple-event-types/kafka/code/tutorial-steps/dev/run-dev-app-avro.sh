java -cp build/libs/multiple-event-types-standalone-0.0.1.jar io.confluent.developer.MultiEventAvroProduceConsumeApp configuration/dev.properties 2>&1 | grep 'io.confluent.developer'
