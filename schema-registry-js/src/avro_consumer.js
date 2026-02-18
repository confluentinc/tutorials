const { Kafka } = require("@confluentinc/kafka-javascript").KafkaJS;
const {
  SchemaRegistryClient,
  SerdeType,
  AvroDeserializer,
} = require("@confluentinc/schemaregistry");
const { loadConfigurationFromArgs } = require("./utils");

async function consumerStart() {
  const { kafkaProps, srProps } = loadConfigurationFromArgs(true);

  let consumer = null;

  const disconnect = () => {
    process.off("SIGINT", disconnect);
    process.off("SIGTERM", disconnect);
    if (consumer !== null) {
      consumer
        .commitOffsets()
        .finally(() => consumer.disconnect());
    }
  };
  process.on("SIGINT", disconnect);
  process.on("SIGTERM", disconnect);

  consumer = new Kafka().consumer(kafkaProps);
  const srClient = new SchemaRegistryClient(srProps);
  const deserializer = new AvroDeserializer(srClient, SerdeType.VALUE, {});

  await consumer.connect();
  await consumer.subscribe({ topics: ["readings"] });

  consumer.run({
    eachMessage: async ({ message }) => {
      const decodedMessage = {
        ...message,
        value: await deserializer.deserialize("readings", message.value),
      };
      console.log(decodedMessage.value);
    },
  });
}

consumerStart();
