package io.confluent.developer;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import io.confluent.common.utils.TestUtils;
import io.confluent.developer.serdes.JSONSerde;

public class KafkaStreamsKTableTTLExample {

  /**
   * This is the main topology showing a very simple kstream-ktable join The KTable here is based on
   * an input topic and not created in the middle of a topology from an aggregation
   *
   * @param envProp
   * @return
   */
  public Topology buildTopology(Properties envProp) {
    final StreamsBuilder builder = new StreamsBuilder();

    String inputTopicForStream = envProp.getProperty("input.topic.name");
    String inputTopicForTable = envProp.getProperty("table.topic.name");
    String outputTopic = envProp.getProperty("output.topic.name");

    // TTL part of the topology
    // This could be in a separate application
    // Setting tombstones for records seen past a TTL of MAX_AGE
    final Duration MAX_AGE =
        Duration.ofMinutes(Integer.parseInt(envProp.getProperty("table.topic.ttl.minutes")));
    final Duration SCAN_FREQUENCY =
        Duration.ofSeconds(Integer.parseInt(envProp.getProperty("table.topic.ttl.scan.seconds")));
    final String STATE_STORE_NAME = envProp.getProperty("table.topic.ttl.store.name");

    // adding a custom state store for the TTL transformer which has a key of type string, and a
    // value of type long
    // which represents the timestamp
    final StoreBuilder<KeyValueStore<String, Long>> storeBuilder = Stores.keyValueStoreBuilder(
        Stores.persistentKeyValueStore(STATE_STORE_NAME), Serdes.String(), Serdes.Long());



    builder.addStateStore(storeBuilder);


    // Read the input data.
    final KStream<String, String> stream =
        builder.stream(inputTopicForStream, Consumed.with(Serdes.String(), Serdes.String()));

    final KStream<String, String> stream2 =
        builder.stream(inputTopicForTable, Consumed.with(Serdes.String(), Serdes.String()));

    KTable<String, AggregateObject> table = stream2
        .transform(() -> new TTLKTableTombstoneEmitter<String, String, KeyValue<String, ValueWrapper>>(MAX_AGE,
            SCAN_FREQUENCY, STATE_STORE_NAME), STATE_STORE_NAME)
        .groupByKey(Grouped.with(Serdes.String(), new JSONSerde<ValueWrapper>()))

        .aggregate(AggregateObject::new, (key, value, aggregate) -> {
          System.out.println("aggregate() - value=" + value);
          if (value.isDeleted())
            return null; // signal to tombstone this key in KTable
          else
            return aggregate.add((String) value.getValue());
        }, Materialized.<String, AggregateObject, KeyValueStore<Bytes, byte[]>>as("eventstore")
            .withKeySerde(Serdes.String()).withValueSerde(new JSONSerde<AggregateObject>()));



    // Perform the custom join operation.
    final KStream<String, String> joined = stream.leftJoin(table, (left, right) -> {
      System.out.println("JOINING left=" + left + " right=" + right);
      if (right != null) {
        int size = right.getValues().size();
        return left + " " + right.getValues().get(size - 1); // concat the last value out of the aggregate
      }
      return left;
    });
    // Write the join results back to Kafka.
    joined.to(outputTopic, Produced.with(Serdes.String(), Serdes.String()));

    return builder.build();
  }

  public void createTopics(final Properties envProps) {
    final Map<String, Object> config = new HashMap<>();
    config.put("bootstrap.servers", envProps.getProperty("bootstrap.servers"));
    try (final AdminClient client = AdminClient.create(config)) {

      final List<NewTopic> topics = new ArrayList<>();

      topics.add(new NewTopic(envProps.getProperty("input.topic.name"),
          Integer.parseInt(envProps.getProperty("input.topic.partitions")),
          Short.parseShort(envProps.getProperty("input.topic.replication.factor"))));

      topics.add(new NewTopic(envProps.getProperty("table.topic.name"),
          Integer.parseInt(envProps.getProperty("table.topic.partitions")),
          Short.parseShort(envProps.getProperty("table.topic.replication.factor"))));

      topics.add(new NewTopic(envProps.getProperty("output.topic.name"),
          Integer.parseInt(envProps.getProperty("output.topic.partitions")),
          Short.parseShort(envProps.getProperty("output.topic.replication.factor"))));



      client.createTopics(topics);
    }
  }

  public Properties loadEnvProperties(String fileName) throws IOException {
    final Properties envProps = new Properties();
    final FileInputStream input = new FileInputStream(fileName);
    envProps.load(input);
    input.close();



    // These two settings are only required in this contrived example so that the
    // streamsConfiguration.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 0);
    // streamsConfiguration.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);
    return envProps;
  }

  public static void main(String[] args) throws Exception {

    if (args.length < 1) {
      throw new IllegalArgumentException(
          "This program takes one argument: the path to an environment configuration file.");
    }

    final KafkaStreamsKTableTTLExample instance = new KafkaStreamsKTableTTLExample();
    final Properties envProps = instance.loadEnvProperties(args[0]);

    // Setup the input topic, table topic, and output topic
    instance.createTopics(envProps);

    // Normally these can be run in separate applications but for the purposes of the demo, we
    // just run both streams instances in the same application
    Topology topology = instance.buildTopology(envProps);

    try (final KafkaStreams streams =
        new KafkaStreams(topology, envProps)) {
      final CountDownLatch startLatch = new CountDownLatch(1);
      // Attach shutdown handler to catch Control-C.
      Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
        @Override
        public void run() {
          // streams.cleanUp();
          streams.close(Duration.ofSeconds(5));
          startLatch.countDown();
        }
      });

      // Start the topology.
      streams.start();

      try {
        startLatch.await();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        System.exit(1);
      }
    }
    System.exit(0);
  }
}
