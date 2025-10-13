package io.confluent.developer;

import lombok.Getter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.help.HelpFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Getter
public class ProducerAppArgParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProducerAppArgParser.class);

    private final String kafkaProperties;

    ProducerAppArgParser(String kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    public static ProducerAppArgParser parseOptions(String[] args) {
        Options options = new Options();

        options.addOption(Option.builder("p")
                .longOpt("properties-file")
                .hasArg()
                .argName("Kafka properties file")
                .desc("Path to Kafka properties file")
                .type(String.class)
                .get());

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = HelpFormatter.builder().get();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println("Error parsing command line arguments: " + e.getMessage());
            try {
                formatter.printHelp("producer-app", "Kafka producer app", options, "", true);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            System.exit(1);
        }

        String kafkaProperties = cmd.getOptionValue("properties-file", "kafka.properties");

        boolean helpRequested = cmd.hasOption("help") || cmd.hasOption("h");
        if (helpRequested) {
            printHelp();
            System.exit(0);
        }

        return new ProducerAppArgParser(kafkaProperties);
    }

    public static InputStream streamFromFile(String path) throws IOException {
        LOGGER.info("Loading configuration from file: {}", path);

        // First try loading from classpath
        InputStream is = ProducerAppArgParser.class.getClassLoader().getResourceAsStream(path);
        if (is != null) {
            LOGGER.info("Found configuration file in classpath: {}", path);
            return is;
        }

        // If not found in classpath, try the filesystem
        File file = new File(path);
        if (file.exists()) {
            LOGGER.info("Found configuration file at: {}", file.getAbsolutePath());
            return new FileInputStream(file);
        }

        throw new IOException("Configuration file not found in classpath or at path: " + path);
    }

    private static void printHelp() {
        System.out.println("Kafka Producer App - Command Line Arguments");
        System.out.println("--------------------------------------------");
        System.out.println("This application produces a bunch of strings to a Kafka topic.");
        System.out.println();
        System.out.println("Optional Arguments:");
        System.out.println("  -p, --kafka-properties <path>  Kafka properties file");
        System.out.println("                                 (default: kafka.properties)");
        System.out.println("  -h, --help                     Display this help message");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java -jar kafka-consumer-comparison-app.jar");
    }

}
