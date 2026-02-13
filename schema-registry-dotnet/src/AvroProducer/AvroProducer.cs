using Confluent.Kafka;
using Confluent.SchemaRegistry;
using Confluent.SchemaRegistry.Serdes;
using io.confluent.developer.avro;
using System;
using System.Threading.Tasks;

class AvroProducer
{
    static async Task Main(string[] args)
    {
        var options = CommandLineOptions.Parse(args, "Avro Producer");
        await RunProducer(options.KafkaPropertiesFile, options.SchemaRegistryPropertiesFile);
    }

    static async Task RunProducer(System.IO.FileInfo kafkaPropertiesFile, System.IO.FileInfo srPropertiesFile)
    {
        var producerConfig = Properties.LoadProducerConfig(kafkaPropertiesFile.FullName);
        var schemaRegistryConfig = Properties.LoadSchemaRegistryConfig(srPropertiesFile.FullName);
        var avroSerializerConfig = new AvroSerializerConfig();

        using (var schemaRegistry = new CachedSchemaRegistryClient(schemaRegistryConfig))
        using (var producer =
            new ProducerBuilder<string, TempReading>(producerConfig)
                .SetValueSerializer(new AvroSerializer<TempReading>(schemaRegistry, avroSerializerConfig))
                .Build())
        {
            var random = new Random();

            for (int i = 0; i < 10; i++)
            {
                var deviceId = random.Next(1, 5).ToString(); // Random device ID between 1 and 4
                var temperature = (float)(random.NextDouble() * 50.0 + 50.0); // Random temp between 50.0 and 100.0

                TempReading reading = new TempReading { deviceId = deviceId, temperature = temperature };
                await producer
                    .ProduceAsync(Constants.Topic, new Message<string, TempReading> { Key = deviceId, Value = reading });
            }

            producer.Flush(TimeSpan.FromSeconds(10));
            Console.WriteLine($"10 messages produced to topic {Constants.Topic}");
        }
    }
}
