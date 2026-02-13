using Confluent.Kafka;
using Confluent.Kafka.SyncOverAsync;
using Confluent.SchemaRegistry;
using Confluent.SchemaRegistry.Serdes;
using io.confluent.developer.avro;
using System;
using System.Threading;

class AvroConsumer
{
    static void Main(string[] args)
    {
        var options = CommandLineOptions.Parse(args, "Avro Consumer");
        RunConsumer(options.KafkaPropertiesFile, options.SchemaRegistryPropertiesFile);
    }

    static void RunConsumer(System.IO.FileInfo kafkaPropertiesFile, System.IO.FileInfo srPropertiesFile)
    {
        var consumerConfig = Properties.LoadConsumerConfig(kafkaPropertiesFile.FullName);
        var schemaRegistryConfig = Properties.LoadSchemaRegistryConfig(srPropertiesFile.FullName);

        using (var cts = new CancellationTokenSource())
        using (var schemaRegistry = new CachedSchemaRegistryClient(schemaRegistryConfig))
        using (var consumer =
            new ConsumerBuilder<string, TempReading>(consumerConfig)
                .SetValueDeserializer(new AvroDeserializer<TempReading>(schemaRegistry).AsSyncOverAsync())
                .SetErrorHandler((_, e) => Console.WriteLine($"Error: {e.Reason}"))
                .Build())
        {
            consumer.Subscribe(Constants.Topic);

            try
            {
                while (true)
                {
                    try
                    {
                        var consumeResult = consumer.Consume(cts.Token);
                        var reading = consumeResult.Message.Value;
                        Console.WriteLine($"Consumed reading deviceId: {reading.deviceId}, temperature: {reading.temperature}");
                    }
                    catch (ConsumeException e)
                    {
                        Console.WriteLine($"Consume error: {e.Error.Reason}");
                    }
                }
            }
            catch (OperationCanceledException)
            {
                consumer.Close();
            }
            cts.Cancel();
        }
    }
}
