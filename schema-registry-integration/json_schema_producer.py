import argparse
import logging
import uuid

from confluent_kafka import Producer
from confluent_kafka.schema_registry import SchemaRegistryClient, header_schema_id_serializer
from confluent_kafka.schema_registry.json_schema import JSONSerializer
from confluent_kafka.serialization import SerializationContext, MessageField, StringSerializer
from faker import Faker
from jproperties import Properties

from user import User

logger = logging.getLogger(__name__)

def delivery_report(err, msg):
    if err is not None:
        print(f"Delivery failed for User record {msg.key().decode('utf-8')}: {err}")
        return
    print(f"Successfully produced message for user_id {msg.key().decode('utf-8')}")

if __name__ == '__main__':

    parser = argparse.ArgumentParser(description="Kafka Producer with Schema Registry")
    parser.add_argument("--kafka-properties-file", type=str, required=True, help="Kafka client properties file")
    parser.add_argument("--sr-properties-file", type=str, required=True, help="Schema Registry client properties file")
    parser.add_argument("--schema-file", type=str, required=True, help="JSON Schema file")
    args = parser.parse_args()

    with open(args.schema_file) as f:
        schema_str = f.read()

    schema_registry_conf = Properties()
    with open(args.sr_properties_file, 'rb') as f:
        schema_registry_conf.load(f, 'utf-8')

    with (SchemaRegistryClient(schema_registry_conf.properties) as schema_registry_client):
        json_serializer = JSONSerializer(schema_registry_client=schema_registry_client,
                                         schema_str=schema_str,
                                         to_dict=User.user_to_dict,
                                         conf={'schema.id.serializer': header_schema_id_serializer})

        string_serializer = StringSerializer('utf_8')

        producer_conf = Properties()
        with open(args.kafka_properties_file, 'rb') as f:
            producer_conf.load(f, 'utf-8')

        with Producer(producer_conf.properties) as producer:
            fake = Faker()
            for i in range(10):
                # invoke on_delivery callbacks from prior calls to produce()
                producer.poll(0.0)
                try:
                    user_id = str(uuid.uuid4())
                    user = User(
                        user_id=user_id,
                        name=fake.name(),
                        email=fake.email()
                    )
                    topic = 'users'
                    headers = {}
                    serialized_value = json_serializer(user, SerializationContext(topic, MessageField.VALUE, headers))
                    producer.produce(
                        topic=topic,
                        key=string_serializer(user_id),
                        value=serialized_value,
                        headers=headers,
                        on_delivery=delivery_report
                    )
                except Exception as e:
                    logger.error(e, stack_info=True, exc_info=True)
                    continue