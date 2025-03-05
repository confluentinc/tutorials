<!-- title: How to integration test a Kafka application with a native (non-JVM) Kafka binary with Testcontainers -->
<!-- description: In this tutorial, learn how to integration test a Kafka application with a native (non-JVM) Kafka binary with Testcontainers, with step-by-step instructions and supporting code. -->

# How to integration test a Kafka application with a native (non-JVM) Kafka binary with Testcontainers

In this tutorial, we will use the `apache/kafka-native` Docker Image released in Apache Kafka® 3.8 to integration test a basic event routing Kafka consumer / producer application. This [GraalVM](https://www.graalvm.org/)-based image runs a [native binary](https://www.graalvm.org/latest/reference-manual/native-image/) Kafka broker running in KRaft [combined mode](https://kafka.apache.org/documentation/#kraft_role) by default (i.e., it serves as both broker and KRaft controller). As a native binary executable, it offers the following test scenario benefits compared to the JVM-based `apache/kafka` image:

1. Smaller image size (faster download time)
2. Faster startup time
3. Lower memory usage

Given these benefits, this image is well-suited for non-production development and testing scenarios that require an actual Kafka broker. [Testcontainers](https://java.testcontainers.org/modules/kafka/) supports this image as of version `1.20.1` of `org.testcontainers`'s `kafka` module.

Testing in this way is as easy as declaring the [Testcontainers Kafka dependency](https://mvnrepository.com/artifact/org.testcontainers/kafka/1.20.5) in your dependency manager and then writing a test like this:

```java
    try (KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:<VERSION>"))) {
        kafka.start();

        // Instantiate and start your application with kafka.getBootstrapServers() as your bootstrap servers endpoint

        // Generate inputs. E.g., if the application is a consumer, then create a Producer instantiated with 
        // properties that include:
        //
        //     properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());

        // Collect outputs. E.g., if the application produces events, then create a Consumer instantiated with 
        // properties that include:
        //
        //     properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());

        // Assert that collected outputs are as expected!
    }
```
        
# A concrete example

Let's look at a concrete Kafka application. The application included in this tutorial, `KafkaPrimalityRouter`, is an 
event routing application that routes input numbers to three potential output topics: one for prime numbers, one for
composite numbers, and a third dead-letter queue where we will send any naughty events (e.g., if the event is not 
deserializable as an integer).

_*Note that this kind of simple routing logic would be better implemented in Kafka Streams by implementing a 
[`TopicNameExtractor`](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.7.0/org/apache/kafka/streams/processor/TopicNameExtractor.html)
that dynamically routes events as described above. We're just having some math fun with a basic consumer / producer application for 
testing demonstration purposes.*_

The anatomy of `KafkaPrimalityRouter` has a `main` method that mostly just does some argument parsing and then hands off
to a function that does the event routing given injected `Producer` and `Consumer` instances. Specifically, `runConsume`
contains the consume loop and routing logic / DLQ handling:

```java
    while (keepConsuming) {
        final ConsumerRecords<byte[], byte[]> consumerRecords = consumer.poll(Duration.ofSeconds(1));
        for (ConsumerRecord<byte[], byte[]> record : consumerRecords) {
            byte[] key = record.key();
            Integer keyInt;
            try {
                keyInt = intDeserializer.deserialize("dummy", key);
            } catch (SerializationException e) {
                dlqProducer.send(new ProducerRecord<>(DLQ_TOPIC, key, record.value()));
                continue;
            }
            String destinationTopic = isPrime(keyInt) ? PRIME_TOPIC : COMPOSITE_TOPIC;

            producer.send(new ProducerRecord<>(destinationTopic, keyInt, keyInt));
        }
    }
```

This `main` / `runConsume` boundary makes it easy to also unit test the components of the application more surgically using [`MockConsumer`](https://kafka.apache.org/38/javadoc/org/apache/kafka/clients/consumer/MockConsumer.html)
and [`MockProducer`](https://kafka.apache.org/38/javadoc/org/apache/kafka/clients/producer/MockProducer.html). Such tests aren't included
in this example, but you may refer to [this test](https://github.com/confluentinc/tutorials/blob/master/kafka-producer-application/kafka/src/test/java/io/confluent/developer/KafkaProducerApplicationTest.java) from [this tutorial](https://developer.confluent.io/confluent-tutorials/kafka-producer-application/kafka/)
for an example.

Now, head over to the `KafkaPrimalityRouterTest.testPrimalityRouter` method to see an example of a Testcontainers-based 
integration test. The test kicks off the application in a separate thread, produces some good (integer) and bad (string)
events, and then consumes from the app's output topics to validate that the events were routed as expected.

# Run the integration test

To run the integration test, first clone the `confluentinc/tutorials` GitHub repository (if you haven't already) and navigate to the `tutorials` directory:

```shell
git clone git@github.com:confluentinc/tutorials.git
cd tutorials
```

Since we are using Testcontainers, install and start [Docker Desktop](https://docs.docker.com/desktop/) or 
[Docker Engine](https://docs.docker.com/engine/install/) if you don't already have it. Verify that Docker is set up and 
running properly by ensuring that no errors are output when you run `docker info` in your terminal.

To run the test, use the provided Gradle Wrapper:

```shell
./gradlew clean :kafka-native-testcontainers:kafka:test --info  
```

# Performance comparison to JVM-based image

You can compare test runtimes between the `apache/kafka-native` and `apache/kafka` images by replacing this line in the test:

```java
    try (KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.0.0"))) {
```

with this:

```java
    try (KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"))) {
```

Now you can compare test performance both with the images downloaded and without. First, make sure that the images aren't available locally:

```noformat
docker rmi apache/kafka-native:4.0.0
docker rmi apache/kafka:4.0.0
```

Next, run the test twice, once using the `apache/kafka` image, and again using the `apache/kafka-native` image. Test
runs from a laptop with 300 Mbps download speed resulted in the following performance numbers:

| Image                 | Image Download Time | Container Startup Time | Test Execution Time | Total Time |
|:----------------------|--------------------:|-----------------------:|--------------------:|-----------:|
| apache/kafka          |               6 sec |                3.5 sec |               5 sec |   14.5 sec |
| apache/kafka-native   |             1.5 sec |                0.6 sec |             4.5 sec |    6.6 sec |


Note that the image download time only applies the first time that you run the test, i.e., a subsequent test
run on the same laptop takes about 8.5 seconds using the `apache/kafka` image and 5.1 seconds using the `apache/kafka-native` image. 
Also, keep in mind that this integration test is on the slow and heavyweight side, e.g., it takes time to validate that no more 
events show up after the expected events show up, a condition that requires waiting 200 ms in the test:

```java
    // make sure no more events show up in prime / composite topics
    assertEquals(0, consumer.poll(Duration.ofMillis(200)).count());
```

So, a more streamlined test that doesn't wait for unexpected events would run much more quickly, making the shorter image
download and container startup times that much more valuable for development and automated testing.
