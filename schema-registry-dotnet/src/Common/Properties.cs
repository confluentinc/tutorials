using Confluent.Kafka;
using Confluent.SchemaRegistry;

public class Properties
{
    public static Dictionary<string, string> LoadPropertiesFile(string filePath)
    {
        var properties = new Dictionary<string, string>();
        foreach (var line in File.ReadAllLines(filePath))
        {
            var trimmedLine = line.Trim();
            if (string.IsNullOrEmpty(trimmedLine) || trimmedLine.StartsWith("#"))
                continue;

            var separatorIndex = trimmedLine.IndexOf('=');
            if (separatorIndex > 0)
            {
                var key = trimmedLine.Substring(0, separatorIndex).Trim();
                var value = trimmedLine.Substring(separatorIndex + 1).Trim();
                properties[key] = value;
            }
        }
        return properties;
    }

    public static ProducerConfig LoadProducerConfig(string filePath)
    {
        var properties = LoadPropertiesFile(filePath);
        var config = new ProducerConfig();

        foreach (var kvp in properties)
        {
            config.Set(kvp.Key, kvp.Value);
        }

        return config;
    }

    public static ConsumerConfig LoadConsumerConfig(string filePath)
    {
        var properties = LoadPropertiesFile(filePath);
        var config = new ConsumerConfig();

        foreach (var kvp in properties)
        {
            config.Set(kvp.Key, kvp.Value);
        }
        config.Set("group.id", "schema-registry-dotnet-example-group");
        config.Set("auto.offset.reset", "earliest");

        return config;
    }

    public static SchemaRegistryConfig LoadSchemaRegistryConfig(string filePath)
    {
        var properties = LoadPropertiesFile(filePath);
        if (!properties.TryGetValue("url", out var url))
        {
            throw new KeyNotFoundException("Missing 'url' property in Schema Registry configuration.");
        }
        var config = new SchemaRegistryConfig()
        {
            Url = url
        };
        if (properties.TryGetValue("basic.auth.user.info", out var basicAuthUserInfo))
        {
            config.BasicAuthUserInfo = basicAuthUserInfo;
        }
        return config;
    }
}
