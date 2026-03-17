import argparse

from confluent_kafka import Consumer
from jproperties import Properties

if __name__ == '__main__':

    parser = argparse.ArgumentParser(description="Kafka Consumer with Schema Registry")
    parser.add_argument("--kafka-properties-file", type=str, required=True, help="Kafka client properties file")
    args = parser.parse_args()

    consumer_conf = Properties()
    consumer_conf['group.id'] = 'my-consumer-group'
    consumer_conf['auto.offset.reset'] = 'earliest'
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

                print("Consumed event from topic {topic}: key = {key:12} value = {value:12}".format(
                    topic=msg.topic(), key=msg.key().decode('utf-8'), value=msg.value().decode('utf-8')))
            except KeyboardInterrupt:
                break
            except Exception as e:
                print(f"Error occurred: {e}")
