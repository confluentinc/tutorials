import argparse

from confluent_kafka import Consumer
from confluent_kafka.schema_registry import SchemaRegistryClient
from confluent_kafka.schema_registry.avro import AvroDeserializer
from confluent_kafka.serialization import SerializationContext, MessageField
from jproperties import Properties

from temp_reading import TempReading


if __name__ == '__main__':

    parser = argparse.ArgumentParser(description="Kafka Consumer with Schema Registry")
    parser.add_argument("--kafka-properties-file", type=str, required=True, help="Kafka client properties file")
    parser.add_argument("--sr-properties-file", type=str, required=True, help="Schema Registry client properties file")
    parser.add_argument("--schema-file", type=str, required=True, help="Schema (.avsc) file")
    args = parser.parse_args()

    with open(args.schema_file) as f:
        schema_str = f.read()

    schema_registry_conf = Properties()
    with open(args.sr_properties_file, 'rb') as f:
        schema_registry_conf.load(f, 'utf-8')

    schema_registry_client = SchemaRegistryClient(schema_registry_conf.properties)
    avro_deserializer = AvroDeserializer(schema_registry_client, schema_str, TempReading.dict_to_reading)

    consumer_conf = Properties()
    consumer_conf['group.id'] = 'kafka-sr-python-group'
    consumer_conf['auto.offset.reset'] = 'earliest'
    with open(args.kafka_properties_file, 'rb') as f:
        consumer_conf.load(f, 'utf-8')

    with Consumer(consumer_conf.properties) as consumer:
        consumer.subscribe(['readings'])
        while True:
            try:
                # SIGINT can't be handled when polling, limit timeout to 1 second.
                msg = consumer.poll(1.0)
                if msg is None:
                    continue
                if msg.error():
                    print(f"Consumer error: {msg.error()}")
                    continue

                reading = avro_deserializer(msg.value(), SerializationContext(msg.topic(), MessageField.VALUE))
                if reading is not None:
                    print(f"Consumed reading: device_id = {reading.device_id}, temperature = {reading.temperature}")
            except KeyboardInterrupt:
                break
            except Exception as e:
                print(f"Error occurred: {e}")
