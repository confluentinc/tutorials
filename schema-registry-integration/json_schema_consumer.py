import argparse

from confluent_kafka import Consumer
from confluent_kafka.schema_registry import SchemaRegistryClient, dual_schema_id_deserializer
from confluent_kafka.schema_registry.json_schema import JSONDeserializer
from confluent_kafka.serialization import SerializationContext, MessageField
from jproperties import Properties

from user import User


if __name__ == '__main__':

    parser = argparse.ArgumentParser(description="Kafka Consumer with Schema Registry")
    parser.add_argument("--kafka-properties-file", type=str, required=True, help="Kafka client properties file")
    parser.add_argument("--sr-properties-file", type=str, required=True, help="Schema Registry client properties file")
    parser.add_argument("--schema-file", type=str, required=True, help="JSON Schema file")
    args = parser.parse_args()

    with open(args.schema_file) as f:
        schema_str = f.read()

    schema_registry_conf = Properties()
    with open(args.sr_properties_file, 'rb') as f:
        schema_registry_conf.load(f, 'utf-8')

    schema_registry_client = SchemaRegistryClient(schema_registry_conf.properties)
    deserializer_config = {
        'schema.id.deserializer': dual_schema_id_deserializer
    }

    json_deserializer = JSONDeserializer(schema_registry_client=schema_registry_client,
                                         schema_str=schema_str,
                                         conf=deserializer_config,
                                         from_dict=User.dict_to_user)

    consumer_conf = Properties()
    consumer_conf['group.id'] = 'my-consumer-group'
    consumer_conf['auto.offset.reset'] = 'latest'
    with open(args.kafka_properties_file, 'rb') as f:
        consumer_conf.load(f, 'utf-8')

    with Consumer(consumer_conf.properties) as consumer:
        consumer.subscribe(['users'])
        while True:
            try:
                # SIGINT can't be handled when polling, limit timeout to 1 second.
                msg = consumer.poll(1.0)
                if msg is None:
                    continue
                if msg.error():
                    print(f"Consumer error: {msg.error()}")
                    continue
                headers = msg.headers() or []
                user = json_deserializer(msg.value(), SerializationContext(msg.topic(), MessageField.VALUE, headers))
                if user is not None:
                    print(f"Consumed user: user_id = {user.user_id}, name = {user.name}, email = {user.email}")
            except KeyboardInterrupt:
                break
            except Exception as e:
                print(f"Error occurred: {e}")
