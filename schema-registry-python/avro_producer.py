import argparse
import random

from confluent_kafka import Producer
from confluent_kafka.schema_registry import SchemaRegistryClient
from confluent_kafka.schema_registry.avro import AvroSerializer
from confluent_kafka.serialization import SerializationContext, MessageField, StringSerializer
from jproperties import Properties

from temp_reading import TempReading


def delivery_report(err, msg):
    if err is not None:
        print(f"Delivery failed for TempReading record {msg.key().decode('utf-8')}: {err}")
        return
    print(f"Successfully produced message for device_id {msg.key().decode('utf-8')}")

if __name__ == '__main__':

    parser = argparse.ArgumentParser(description="Kafka Producer with Schema Registry")
    parser.add_argument("--kafka-properties-file", type=str, required=True, help="Kafka client properties file")
    parser.add_argument("--sr-properties-file", type=str, required=True, help="Schema Registry client properties file")
    parser.add_argument("--schema-file", type=str, required=True, help="Schema (.avsc) file")
    args = parser.parse_args()

    with open(args.schema_file) as f:
        schema_str = f.read()

    schema_registry_conf = Properties()
    with open(args.sr_properties_file, 'rb') as f:
        schema_registry_conf.load(f, 'utf-8')

    # Use context manager for SchemaRegistryClient to ensure proper cleanup
    with SchemaRegistryClient(schema_registry_conf.properties) as schema_registry_client:
        avro_serializer = AvroSerializer(schema_registry_client, schema_str, TempReading.reading_to_dict)

        string_serializer = StringSerializer('utf_8')

        producer_conf = Properties()
        with open(args.kafka_properties_file, 'rb') as f:
            producer_conf.load(f, 'utf-8')

        with Producer(producer_conf.properties) as producer:
            for i in range(10):
                # invoke on_delivery callbacks from prior calls to produce()
                producer.poll(0.0)
                try:
                    device_id = str(random.randint(0, 4))
                    reading = TempReading(
                        device_id=device_id,
                        temperature=70.0 + random.random() * 40.0
                    )
                    topic = 'readings'
                    producer.produce(
                        topic=topic,
                        key=string_serializer(device_id),
                        value=avro_serializer(reading, SerializationContext(topic, MessageField.VALUE)),
                        on_delivery=delivery_report,
                    )
                except Exception as e:
                    print(f"Error producing: {e}")
                    continue