package io.confluent.developer;

import com.github.javafaker.Faker;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Util implements AutoCloseable {

    private final Logger logger = LoggerFactory.getLogger(Util.class);
    private ExecutorService executorService = Executors.newFixedThreadPool(1);

    public class Randomizer implements AutoCloseable, Runnable {
        private Properties props;
        private String topic;
        private Producer<String, String> producer;
        private boolean closed;

        public Randomizer(Properties producerProps, String topic) {
            this.closed = false;
            this.topic = topic;
            this.props = producerProps;
            this.props.setProperty("client.id", "faker");
        }

        public void run() {
            try (KafkaProducer producer = new KafkaProducer<String, String>(props)) {
                Faker faker = new Faker();
                while (!closed) {
                    try {
                        Object result = producer.send(new ProducerRecord<>(
                                this.topic,
                                faker.chuckNorris().fact())).get();
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                    }
                }
            } catch (Exception ex) {
                logger.error(ex.toString());
            }
        }
        public void close()  {
            closed = true;
        }
    }

    public Randomizer startNewRandomizer(Properties producerProps, String topic) {
        Randomizer rv = new Randomizer(producerProps, topic);
        executorService.submit(rv);
        return rv;
    }

    public void createTopics(final Properties allProps, List<NewTopic> topics)
            throws InterruptedException, ExecutionException, TimeoutException {
        try (final AdminClient client = AdminClient.create(allProps)) {
            logger.info("Creating topics");

            client.createTopics(topics).values().forEach( (topic, future) -> {
                try {
                    future.get();
                } catch (Exception ex) {
                    logger.info(ex.toString());
                }
            });

            Collection<String> topicNames = topics
                .stream()
                .map(t -> t.name())
                .collect(Collectors.toCollection(LinkedList::new));

            logger.info("Asking cluster for topic descriptions");
            client
                .describeTopics(topicNames)
                .allTopicNames()
                .get(10, TimeUnit.SECONDS)
                .forEach((name, description) -> logger.info("Topic Description: {}", description.toString()));
        }
    }

    public void close() {
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }
}
