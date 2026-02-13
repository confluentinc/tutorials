using System.CommandLine;

public class CommandLineOptions
{
    public FileInfo? KafkaPropertiesFile { get; set; }
    public FileInfo? SchemaRegistryPropertiesFile { get; set; }

    public static CommandLineOptions Parse(string[] args, string applicationName)
    {
        Option<FileInfo> kafkaPropertiesFileOption = new("--kafka-properties-file")
        {
            Description = "Path to Kafka properties file",
            Required = true
        };
        Option<FileInfo> srPropertiesFileOption = new("--sr-properties-file")
        {
            Description = "Path to Schema Registry properties file",
            Required = true
        };

        RootCommand rootCommand = new(applicationName);
        rootCommand.Options.Add(kafkaPropertiesFileOption);
        rootCommand.Options.Add(srPropertiesFileOption);

        ParseResult parseResult = rootCommand.Parse(args);

        return new CommandLineOptions
        {
            KafkaPropertiesFile = parseResult.GetValue(kafkaPropertiesFileOption),
            SchemaRegistryPropertiesFile = parseResult.GetValue(srPropertiesFileOption)
        };
    }
}
