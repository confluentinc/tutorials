<!-- title: How to filter duplicate events per-time window from a Kafka topic with Kafka Streams -->
<!-- description: In this tutorial, learn how to filter duplicate events per-time window from a Kafka topic with Kafka Streams, with step-by-step instructions and supporting code. -->

# How to filter duplicate events per-time window from a Kafka topic with Kafka Streams

Consider a topic with events that represent clicks on a website. Each event contains an IP address, a URL, and a timestamp. In this tutorial, we'll write a program that filters duplicate click events by the IP address within a window of time.


```java
 builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), clicksSerde))
        .processValues(() -> new DeduplicationProcessor<>(windowSize.toMillis(), (key, value) -> value.ip()), STORE_NAME)
        .filter((k, v) -> v != null)
        .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), clicksSerde));
```
Note how the Kafka Streams topology uses a custom processor  the `DeduplicationProcessor` and a `Window Store`, to filter out the duplicate IP addresses. Events are de-duped within a 2-minute window, and unique clicks are produced to a new topic.

Let's take a look at the core logic of the `DeduplicationProcessor`:
```java
 public void process(FixedKeyRecord<K, V> fixedKeyRecord) {
            K key = fixedKeyRecord.key();
            V value = fixedKeyRecord.value();
            final E eventId = idExtractor.apply(key, value);
            if (eventId == null) {
                context.forward(fixedKeyRecord);  <1>
            } else {
                final V output;
                if (isDuplicate(eventId)) {
                    output = null;            <2>
                    updateTimestampOfExistingEventToPreventExpiry(eventId, context.currentStreamTimeMs());
                } else {
                    output = value;       <3>
                    rememberNewEvent(eventId, context.currentStreamTimeMs());
                }
                context.forward(fixedKeyRecord.withValue(output)); <4>
            }
        }
```
1. If the event id is not found, forward the record downstream.
2. If the record is a duplicate set the value to `null` and forward it and update the expiration timestamp. A downstream filter operator will remove the null value.
3. Otherwise, the record is not a duplicate, set the timestamp for expiration and forward the value.
4. The processor uses the`forward` method to send the record to the next processor in the topology.

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
  --environment-name kafka-streams-deduplication-windowed-env \
  --kafka-cluster-name kafka-streams-deduplication-windowed-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./deduplication-windowed/kstreams/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create distinct-input-topic
confluent kafka topic create distinct-output-topic
```

Start a console producer:

```shell
confluent kafka topic produce distinct-input-topic
```

Enter a few JSON-formatted click events:

```plaintext
{"ip":"10.0.0.1", "url":"https://docs.confluent.io/current/tutorials/examples/kubernetes/gke-base/docs/index.html", "timestamp":"2025-08-16T14:53:43+00:00"}
{"ip":"10.0.0.2", "url":"https://www.confluent.io/hub/confluentinc/kafka-connect-datagen", "timestamp":"2025-08-16T14:53:43+00:01"}
{"ip":"10.0.0.3", "url":"https://www.confluent.io/hub/confluentinc/kafka-connect-datagen", "timestamp":"2025-08-16T14:53:43+00:03"}
{"ip":"10.0.0.1", "url":"https://docs.confluent.io/current/tutorials/examples/kubernetes/gke-base/docs/index.html", "timestamp":"2025-08-16T14:53:43+00:00"}
{"ip":"10.0.0.2", "url":"https://www.confluent.io/hub/confluentinc/kafka-connect-datagen", "timestamp":"2025-08-16T14:53:43+00:01"}
{"ip":"10.0.0.3", "url":"https://www.confluent.io/hub/confluentinc/kafka-connect-datagen", "timestamp":"2025-08-16T14:53:43+00:03"}
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew deduplication-windowed:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd deduplication-windowed/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/find-distinct-standalone.jar \
    io.confluent.developer.FindDistinctEvents \
    ./src/main/resources/cloud.properties
```

Validate that you see only one event per IP address in the `distinct-output-topic` topic.

```shell
confluent kafka topic consume distinct-output-topic -b
```

You should see:

```shell
{"ip":"10.0.0.1","url":"https://docs.confluent.io/current/tutorials/examples/kubernetes/gke-base/docs/index.html","timestamp":"2025-08-16T14:53:43+00:00"}
{"ip":"10.0.0.2","url":"https://www.confluent.io/hub/confluentinc/kafka-connect-datagen","timestamp":"2025-08-16T14:53:43+00:01"}
{"ip":"10.0.0.3","url":"https://www.confluent.io/hub/confluentinc/kafka-connect-datagen","timestamp":"2025-08-16T14:53:43+00:03"}
```

## Clean up

When you are finished, delete the `kafka-streams-deduplication-windowed-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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
  kafka-topics --bootstrap-server localhost:9092 --create --topic distinct-input-topic
  kafka-topics --bootstrap-server localhost:9092 --create --topic distinct-output-topic
  ```

  Start a console producer:

  ```shell
  kafka-console-producer --bootstrap-server localhost:9092 --topic distinct-input-topic
  ```

  Enter a few JSON-formatted click events:

  ```plaintext
  {"ip":"10.0.0.1", "url":"https://docs.confluent.io/current/tutorials/examples/kubernetes/gke-base/docs/index.html", "timestamp":"2025-08-16T14:53:43+00:00"}
  {"ip":"10.0.0.2", "url":"https://www.confluent.io/hub/confluentinc/kafka-connect-datagen", "timestamp":"2025-08-16T14:53:43+00:01"}
  {"ip":"10.0.0.3", "url":"https://www.confluent.io/hub/confluentinc/kafka-connect-datagen", "timestamp":"2025-08-16T14:53:43+00:03"}
  {"ip":"10.0.0.1", "url":"https://docs.confluent.io/current/tutorials/examples/kubernetes/gke-base/docs/index.html", "timestamp":"2025-08-16T14:53:43+00:00"}
  {"ip":"10.0.0.2", "url":"https://www.confluent.io/hub/confluentinc/kafka-connect-datagen", "timestamp":"2025-08-16T14:53:43+00:01"}
  {"ip":"10.0.0.3", "url":"https://www.confluent.io/hub/confluentinc/kafka-connect-datagen", "timestamp":"2025-08-16T14:53:43+00:03"}
  ```
  
  Enter `Ctrl+C` to exit the console producer.

  ## Compile and run the application

  On your local machine, compile the app:

  ```shell
  ./gradlew deduplication-windowed:kstreams:shadowJar
  ```

  Navigate into the application's home directory:

  ```shell
  cd deduplication-windowed/kstreams
  ```

  Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

  ```shell
  java -cp ./build/libs/find-distinct-standalone.jar \
      io.confluent.developer.FindDistinctEvents \
      ./src/main/resources/local.properties
  ```

  Validate that you see only one event per IP address in the `distinct-output-topic` topic. In the broker container shell:

  ```shell
  kafka-console-consumer --bootstrap-server localhost:9092 --topic distinct-output-topic --from-beginning
  ```

  You should see:

  ```shell
  {"ip":"10.0.0.1","url":"https://docs.confluent.io/current/tutorials/examples/kubernetes/gke-base/docs/index.html","timestamp":"2025-08-16T14:53:43+00:00"}
  {"ip":"10.0.0.2","url":"https://www.confluent.io/hub/confluentinc/kafka-connect-datagen","timestamp":"2025-08-16T14:53:43+00:01"}
  {"ip":"10.0.0.3","url":"https://www.confluent.io/hub/confluentinc/kafka-connect-datagen","timestamp":"2025-08-16T14:53:43+00:03"}
  ```

  ## Clean up

  From your local machine, stop the broker container:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml down
  ```
</details>
