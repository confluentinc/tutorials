<!-- title: How to dynamically choose output topics with Kafka Streams -->
<!-- description: In this tutorial, learn how to dynamically choose output topics with Kafka Streams, with step-by-step instructions and supporting code. -->

# How to dynamically choose output topics with Kafka Streams

Consider a situation where you want to direct the output of different records to different topics, like a "topic exchange." 
In this tutorial, you'll learn how to instruct Kafka Streams to choose the output topic at runtime, 
based on information in each record's header, key, or value. 

```java
  builder.stream(INPUT_TOPIC, Consumed.with(stringSerde, orderSerde))
         .mapValues(orderProcessingSimulator)
        .to(orderTopicNameExtractor, Produced.with(stringSerde, completedOrderSerde));
```

Here's our example topology.  To dynamically route records to different topics, you'll use an instance of the [TopicNameExtractor](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/processor/TopicNameExtractor.html).  As shown in here, you provide the `TopicNameExtractor` to 
the overloaded [KStream.to](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/kstream/KStream.html#to-org.apache.kafka.streams.processor.TopicNameExtractor-org.apache.kafka.streams.kstream.Produced-).

Here's the `TopicNameExtractor` used in this example.  It uses information from the value to determine which topic Kafka Streams should use
for this record.

```java
final TopicNameExtractor<String, CompletedOrder> orderTopicNameExtractor = (key, completedOrder, recordContext) -> {
              final String compositeId = completedOrder.id();
              final String skuPart = compositeId.substring(compositeId.indexOf('-') + 1, 5);
              final String outTopic;
              if (skuPart.equals("QUA")) {
                  outTopic = SPECIAL_ORDER_OUTPUT_TOPIC;
              } else {
                  outTopic = OUTPUT_TOPIC;
              }
              return outTopic;
        };
```

The `TopicNameExtractor` interface has one method, `extract`, which makes it suitable for using a lambda, as shown here.  But remember using a concrete class has the advantage of being directly testable. 

The following steps use Confluent Cloud. To run the tutorial locally with Docker, skip to the `Docker instructions` section at the bottom.

## Prerequisites

* A [Confluent Cloud](https://confluent.cloud/signup) account
* The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
* [Apache Kafka](https://kafka.apache.org/downloads) or [Confluent Platform](https://docs.confluent.io/platform/current/installation/installing_cp/zip-tar.html) (both include the Kafka Streams application reset tool)
* Clone the `confluentinc/tutorials` repository and navigate into its top-level directory:
  ```shell
  git clone git@github.com:confluentinc/tutorials.git
  cd tutorials
  ```

## Create Confluent Cloud resources

Login to your Confluent Cloud account:

```shell
confluent login --prompt --save
```

Install a CLI plugin that will streamline the creation of resources in Confluent Cloud:

```shell
confluent plugin install confluent-quickstart
```

Run the plugin from the top-level directory of the `tutorials` repository to create the Confluent Cloud resources needed for this tutorial. Note that you may specify a different cloud provider (`gcp` or `azure`) or region. You can find supported regions in a given cloud provider by running `confluent kafka region list --cloud <CLOUD>`.

```shell
confluent quickstart \
  --environment-name kafka-streams-dynamic-output-topic-env \
  --kafka-cluster-name kafka-streams-dynamic-output-topic-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./dynamic-output-topic/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create dynamic-topic-input
confluent kafka topic create dynamic-topic-output
confluent kafka topic create special-order-output
```

Start a console producer:

```shell
confluent kafka topic produce dynamic-topic-input
```

Enter a few JSON-formatted orders:

```plaintext
{"id":6, "sku":"COF0003456", "name":"coffee", "quantity":1}
{"id":7, "sku":"QUA000022334", "name":"hand sanitizer", "quantity":2}
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew dynamic-output-topic:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd dynamic-output-topic/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/dynamic-output-topic-standalone.jar \
    io.confluent.developer.KafkaStreamsDynamicOutputTopic \
    ./src/main/resources/cloud.properties
```

Validate that you see the first order in the `dynamic-topic-output` topic and the second in the `special-order-output` topic.

```shell
confluent kafka topic consume dynamic-topic-output -b
```

```shell
confluent kafka topic consume special-order-output -b
```

## Clean up

When you are finished, delete the `kafka-streams-dynamic-output-topic-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

```shell
confluent environment list
```

Delete the environment, including all resources created for this tutorial:

```shell
confluent environment delete <ENVIRONMENT ID>
```

<details>
  <summary>Docker instructions</summary>

  ## Prerequisites

  * Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
  * [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.
  * Clone the `confluentinc/tutorials` repository and navigate into its top-level directory:
    ```shell
    git clone git@github.com:confluentinc/tutorials.git
    cd tutorials
    ```

  ## Start Kafka in Docker

  Start Kafka with the following command run from the top-level `tutorials` repository directory:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml up -d
  ```

  ## Create topics

  Open a shell in the broker container:

  ```shell
  docker exec -it broker /bin/bash
  ```

  Create the input and output topics for the application:

  ```shell
  kafka-topics --bootstrap-server localhost:9092 --create --topic dynamic-topic-input
  kafka-topics --bootstrap-server localhost:9092 --create --topic dynamic-topic-output
  kafka-topics --bootstrap-server localhost:9092 --create --topic special-order-output
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic dynamic-topic-input
  ```

  Enter a few JSON-formatted orders:

  ```plaintext
  {"id":6, "sku":"COF0003456", "name":"coffee", "quantity":1}
  {"id":7, "sku":"QUA000022334", "name":"hand sanitizer", "quantity":2}
  ```

  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew dynamic-output-topic:kstreams:shadowJar
  ```

 Navigate into the application's home directory:

  ```shell
  cd dynamic-output-topic/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/dynamic-output-topic-standalone.jar \
      io.confluent.developer.KafkaStreamsDynamicOutputTopic \
      ./src/main/resources/local.properties
  ```

 Validate that you see the first order in the `dynamic-topic-output` topic and the second in the `special-order-output` topic.

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic dynamic-topic-output --from-beginning
  ```
  
  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic special-order-output --from-beginning
  ```

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
