import argparse
import json
import uuid

from confluent_kafka import Producer
from confluent_kafka.serialization import StringSerializer
from faker import Faker
from jproperties import Properties


def delivery_report(err, msg):
    if err is not None:
        print(f"Delivery failed for User record {msg.key().decode('utf-8')}: {err}")
        return
    print(f"Successfully produced message for user_id {msg.key().decode('utf-8')}")

if __name__ == '__main__':

    parser = argparse.ArgumentParser(description="Kafka Producer with Schema Registry")
    parser.add_argument("--kafka-properties-file", type=str, required=True, help="Kafka client properties file")
    args = parser.parse_args()

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
                user = {
                    'user_id': user_id,
                    'name': fake.name(),
                    'email': fake.email()
                }
                topic = 'users'
                producer.produce(
                    topic=topic,
                    key=string_serializer(user_id),
                    value=string_serializer(json.dumps(user)),
                    on_delivery=delivery_report,
                )
            except Exception as e:
                print(f"Error producing: {e}")
                continue