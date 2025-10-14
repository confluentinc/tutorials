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
public class ConsumerAppArgParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerAppArgParser.class);

    private final String kafkaProperties;
    private final int numConsumers;
    private final int sleepMs;
    private final int totalEvents;
    private final ConsumerType consumerType;

    enum ConsumerType {
        CONSUMER, SHARE_CONSUMER
    }

    ConsumerAppArgParser(String kafkaProperties, ConsumerType consumerType, int numConsumers, int sleepMs, int totalEvents) {
        this.kafkaProperties = kafkaProperties;
        this.consumerType = consumerType;
        this.numConsumers = numConsumers;
        this.sleepMs = sleepMs;
        this.totalEvents = totalEvents;
    }

    public static ConsumerAppArgParser parseOptions(String[] args) {
        Options options = new Options();

        options.addOption(Option.builder("p")
                .longOpt("properties-file")
                .hasArg()
                .argName("Kafka properties file")
                .desc("Path to Kafka properties file")
                .type(String.class)
                .get());

        options.addOption(Option.builder("t")
                .longOpt("consumer-type")
                .hasArg()
                .argName("Consumer Type")
                .desc("Kafka consumer type (CONSUMER or SHARE_CONSUMER)")
                .type(String.class)
                .get());

        options.addOption(Option.builder("n")
                .longOpt("num-consumers")
                .hasArg()
                .argName("Number of Consumers")
                .desc("Number of consumers to start (default: 5, max: 16)")
                .type(Number.class)
                .get());

        options.addOption(Option.builder("e")
                .longOpt("total-events")
                .hasArg()
                .argName("Number of Events")
                .desc("Number of events to consume (default: 1000, max: 1000000)")
                .type(Number.class)
                .get());

        options.addOption(Option.builder("w")
                .longOpt("wait-ms")
                .hasArg()
                .argName("Sleep Time per Event in ms")
                .desc("Sleep Time per Event in ms (default: 50, max: 5000)")
                .type(Number.class)
                .get());

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = HelpFormatter.builder().get();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println("Error parsing command line arguments: " + e.getMessage());
            try {
                formatter.printHelp("consumer-app", "Kafka Consumer testing app", options, "", true);
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

        final int numConsumerArg = Integer.parseInt(cmd.getOptionValue("num-consumers", "5"));
        if (numConsumerArg < 1 || numConsumerArg > 16) {
            throw new IllegalArgumentException("Number of consumers must be between 1 and 16 (inclusive)");
        }

        final int sleepMsArg = Integer.parseInt(cmd.getOptionValue("wait-ms", "50"));
        if (sleepMsArg < 1 || sleepMsArg > 5000) {
            throw new IllegalArgumentException("Per-event sleep time must be between 1 and 5000 ms (inclusive)");
        }

        final int totalEventsArg = Integer.parseInt(cmd.getOptionValue("total-events", "1000"));
        if (totalEventsArg < 1 || totalEventsArg > 1000000) {
            throw new IllegalArgumentException("Number of events to consume must be between 1 and 1,000,000 (inclusive)");
        }

        final ConsumerType consumerType;
        try {
            consumerType = ConsumerType.valueOf(cmd.getOptionValue("consumer-type").toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Consumer type must be consumer or share_consumer");
        }

        return new ConsumerAppArgParser(kafkaProperties, consumerType, numConsumerArg, sleepMsArg, totalEventsArg);
    }

    public static InputStream streamFromFile(String path) throws IOException {
        LOGGER.info("Loading configuration from file: {}", path);

        // First try loading from classpath
        InputStream is = ConsumerApp.class.getClassLoader().getResourceAsStream(path);
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
        System.out.println("Kafka Consumer App - Command Line Arguments");
        System.out.println("--------------------------------------------");
        System.out.println("This application demonstrates performance differences between ");
        System.out.println("Kafka consumers and share consumers.");
        System.out.println();
        System.out.println("Required Arguments:");
        System.out.println("  -t, --consumer-type <type>  Type of consumer (consumer or share_consumer)");
        System.out.println();
        System.out.println("Optional Arguments:");
        System.out.println("  -p, --kafka-properties <path>  Kafka properties file");
        System.out.println("                                 (default: kafka.properties)");
        System.out.println("  -n, --num-consumers    <count> Number of consumers to start");
        System.out.println("                                 (default: 5, max: 16)");
        System.out.println("  -w, --wait-ms          <ms>    Number of ms to sleep when processing each event");
        System.out.println("                                 (default: 50, max: 5000)");
        System.out.println("  -e, --total-events     <num>   Number of events to consume before exiting");
        System.out.println("                                 (default: 1,000, max: 1,000,000)");
        System.out.println("  -h, --help                     Display this help message");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java -jar kafka-consumer-comparison-app.jar \\");
        System.out.println("    --consumer-type share_consumer \\");
        System.out.println("    --num-consumers 16");
    }

}
