const { Kafka } = require("@confluentinc/kafka-javascript").KafkaJS;
const {
  SchemaRegistryClient,
  SerdeType,
  AvroSerializer,
} = require("@confluentinc/schemaregistry");
const { loadConfigurationFromArgs, schema } = require("./utils");

async function producerStart() {
  const { kafkaProps, srProps } = loadConfigurationFromArgs(false);

  const producer = new Kafka().producer(kafkaProps);
  const srClient = new SchemaRegistryClient(srProps);

  const schemaInfo = {
    schemaType: "AVRO",
    schema: JSON.stringify(schema),
  };

  await srClient.register("readings-value", schemaInfo);

  const ser = new AvroSerializer(srClient, SerdeType.VALUE, {
    useLatestVersion: true,
  });

  await producer.connect();

  const res = [];
  for (let i = 0; i < 10; i++) {
    const outgoingMessage = {
      key: "key",
      value: await ser.serialize("readings", {
        deviceId: String(Math.floor(Math.random() * 4) + 1),
        temperature: 50.0 + Math.random() * 50.0,
      }),
    };
    res.push(
      producer.send({
        topic: "readings",
        messages: [outgoingMessage],
      }),
    );
  }
  const deliveryReports = await Promise.all(res);
  console.log("\nProduced " + deliveryReports.length + " readings\n");

  await producer.disconnect();
}

producerStart();
