<!-- title: How to evolve message schemas managed by Schema Registry -->
<!-- description: In this tutorial, learn how to evolve message schemas managed by Schema Registry, with step-by-step instructions and supporting code. -->

# How to evolve message schemas managed by Schema Registry

This tutorial demonstrates how to safely evolve Kafka message schemas managed by Schema Registry. Schema Registry natively supports the ever-changing nature of message schemas by enforcing compatibility rules that ensure producers and consumers can work together even as schemas change. You'll walk through a practical example of evolving a schema by adding a new field and changing a field's data type. The tutorial also shows how to update Kafka producer and consumer applications in the correct order to maintain compatibility throughout the evolution process.

The following steps use Confluent Cloud. To run the tutorial locally with Docker, skip to the Docker instructions section at the bottom.

## Prerequisites

- Java 17
- A [Confluent Cloud](https://confluent.cloud/signup) account
- The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
- Clone the `confluentinc/tutorials` repository and navigate into its top-level directory:
  ```shell
  git clone git@github.com:confluentinc/tutorials.git
  cd tutorials
  ```

## Create Confluent Cloud resources

Log in to your Confluent Cloud account:

```shell
confluent login --prompt --save
```

Install a CLI plugin that will streamline the creation of resources in Confluent Cloud:

```shell
confluent plugin install confluent-quickstart
```

Run the plugin from the top-level directory of the `tutorials` repository to create the Confluent Cloud resources needed for this tutorial.

Note: You may specify a different cloud provider (`gcp` or `azure`) or region. You can find supported regions in a given cloud provider by running `confluent kafka region list --cloud <CLOUD>`.

```shell
confluent quickstart \
  --environment-name schema-evolution-env \
  --kafka-cluster-name schema-evolution-cluster \
  --create-kafka-key \
  --create-sr-key \
  --kafka-java-properties-file ./schema-evolution/src/main/resources/cloud.properties
```

The plugin should complete in under a minute.

## Create topic

Create the topic for the application:

```shell
confluent kafka topic create readings
```

## Compile the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew schema-evolution:shadowJar
```

## Run the producer and consumer

Navigate into the application's home directory:

```shell
cd schema-evolution
```

Run the producer application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/schema-evolution-standalone.jar \
    io.confluent.developer.AvroProducer \
    ./src/main/resources/cloud.properties \
    ./src/main/avro/temp-reading.avsc
```

This will produce ten temperature reading messages that have two fields: a string `deviceId` and float `temperature`.

Next, run the consumer application:

```shell
java -cp ./build/libs/schema-evolution-standalone.jar \
    io.confluent.developer.AvroConsumer \
    ./src/main/resources/cloud.properties
```

You should see output similar to the following:

```shell
[main] INFO io.confluent.developer.AvroConsumer - Consumed event: key = 4, deviceId = 4, temperature = 89.11165
[main] INFO io.confluent.developer.AvroConsumer - Consumed event: key = 3, deviceId = 3, temperature = 98.30217
[main] INFO io.confluent.developer.AvroConsumer - Consumed event: key = 0, deviceId = 0, temperature = 94.170296
[main] INFO io.confluent.developer.AvroConsumer - Consumed event: key = 3, deviceId = 3, temperature = 94.42372
[main] INFO io.confluent.developer.AvroConsumer - Consumed event: key = 3, deviceId = 3, temperature = 93.87649
[main] INFO io.confluent.developer.AvroConsumer - Consumed event: key = 4, deviceId = 4, temperature = 91.70332
[main] INFO io.confluent.developer.AvroConsumer - Consumed event: key = 1, deviceId = 1, temperature = 85.44849
[main] INFO io.confluent.developer.AvroConsumer - Consumed event: key = 3, deviceId = 3, temperature = 83.115486
[main] INFO io.confluent.developer.AvroConsumer - Consumed event: key = 1, deviceId = 1, temperature = 80.96756
[main] INFO io.confluent.developer.AvroConsumer - Consumed event: key = 0, deviceId = 0, temperature = 90.369835
```

Enter `Ctrl+C` to exit the application.

## Evolve the schema

Let's evolve the temperature reading schema in a backward compatible way by adding an optional `factoryId` field, and also widening the type of the `temperature` field from a 4-byte `float` to an 8-byte `double`. These changes are captured in the `./src/main/avro/temp-reading-v2.avsc` schema file. Refer to [this table](https://docs.confluent.io/platform/current/schema-registry/fundamentals/schema-evolution.html#summary) for details on which schema changes are allowed for a given compatibility type and schema format.

Next, review the modified `V2` applications that work with the updated schema. The producer (`AvroProducerV2`) now populates the `factoryId` field:

```java
String factoryId = String.valueOf(rand.nextInt(5));
...
GenericRecord reading = new GenericData.Record(schema);
...
reading.put("factoryId", factoryId);
```

Similarly, the consumer (`AvroConsumerV2`) is updated to log the factory ID to the console, and it handles both float and double temperatures:

```java
double temp = ((Number)value.get("temperature")).doubleValue();
```

Note that, because Avro `GenericRecord` doesn't offer type safety, developers must ensure that the applications using this API evolve in a backward compatible way. As an example of a non-backward compatible change, the expression `(Double)value.get("temperature");` would trigger an exception if run against records produced by `AvroProducer` because a boxed `Float` doesn't automatically get re-boxed to a `Double`. Attempting to cast a `Float` to a `Double` triggers a `RuntimeException` with message `class java.lang.Float cannot be cast to class java.lang.Double`.

## Run updated applications

Since our [schema compatibility type](https://docs.confluent.io/platform/current/schema-registry/fundamentals/schema-evolution.html#compatibility-types) is the default `BACKWARD`, consumers using the new schema can read data produced with the previous schema. Conversely, there is no guarantee that a consumer using the first version of the schema will work with data produced with the updated version. Therefore, we must upgrade consumers _before_ upgrading producers.

Start the upgraded consumer and leave it running:

```shell
java -cp ./build/libs/schema-evolution-standalone.jar \
    io.confluent.developer.AvroConsumerV2 \
    ./src/main/resources/cloud.properties
```

Run the upgraded producer in another terminal:

```shell
java -cp ./build/libs/schema-evolution-standalone.jar \
    io.confluent.developer.AvroProducerV2 \
    ./src/main/resources/cloud.properties \
    ./src/main/avro/temp-reading-v2.avsc
```

Run the original producer again to observe that the new consumer works with it:

```shell
java -cp ./build/libs/schema-evolution-standalone.jar \
  io.confluent.developer.AvroProducer \
  ./src/main/resources/cloud.properties \
  ./src/main/avro/temp-reading.avsc
```

You will see that the upgraded consumer properly handles messages adhering to the original and updated schema:
```plaintext
[main] INFO Consumed event: key = 2, deviceId = 2, factoryId = 2, temperature = 75.96076296305301 (type class java.lang.Double)
[main] INFO Consumed event: key = 1, deviceId = 1, factoryId = 3, temperature = 83.7503375910799 (type class java.lang.Double)
...
[main] INFO Consumed event: key = 1, deviceId = 1, factoryId = N/A, temperature = 85.32722473144531 (type class java.lang.Float)
[main] INFO Consumed event: key = 0, deviceId = 0, factoryId = N/A, temperature = 89.28128051757812 (type class java.lang.Float)
```

## Clean up

When you are finished, delete the `schema-evolution-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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

  - Java 17
  - Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
  - [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.
  - Clone the `confluentinc/tutorials` repository and navigate into its top-level directory:
    ```shell
    git clone git@github.com:confluentinc/tutorials.git
    cd tutorials
    ```

  ## Start Kafka in Docker

  Start Kafka and Schema Registry with the following command run from the top-level `tutorials` repository directory:

  ```shell
  docker compose -f ./docker/docker-compose-kafka-sr.yml up -d
  ```

  ## Create topic

  Open a shell in the broker container:

  ```shell
  docker exec -it broker /bin/bash
  ```

  Create the topic for the application:

  ```shell
  kafka-topics --bootstrap-server localhost:9092 --create --topic readings
  ```

  Exit the broker container by entering `Ctrl+D`.

  ## Compile the application

  Compile the application from the top-level `tutorials` repository directory:

  ```shell
  ./gradlew schema-evolution:shadowJar
  ```

  ## Run the producer and consumer

  Navigate into the application's home directory:

  ```shell
  cd schema-evolution
  ```

  Run the producer application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092` and Schema Registry at `http://localhost:8081`:

  ```shell
  java -cp ./build/libs/schema-evolution-standalone.jar \
      io.confluent.developer.AvroProducer \
      ./src/main/resources/local.properties \
      ./src/main/avro/temp-reading.avsc
  ```

  This will produce ten temperature reading messages that have two fields: a string `deviceId` and float `temperature`.

  Next, run the consumer application:

  ```shell
  java -cp ./build/libs/schema-evolution-standalone.jar \
      io.confluent.developer.AvroConsumer \
      ./src/main/resources/local.properties
  ```

  You should see output similar to the following:

  ```shell
  [main] INFO io.confluent.developer.AvroConsumer - Consumed event: key = 4, deviceId = 4, temperature = 89.11165
  [main] INFO io.confluent.developer.AvroConsumer - Consumed event: key = 3, deviceId = 3, temperature = 98.30217
  [main] INFO io.confluent.developer.AvroConsumer - Consumed event: key = 0, deviceId = 0, temperature = 94.170296
  [main] INFO io.confluent.developer.AvroConsumer - Consumed event: key = 3, deviceId = 3, temperature = 94.42372
  [main] INFO io.confluent.developer.AvroConsumer - Consumed event: key = 3, deviceId = 3, temperature = 93.87649
  [main] INFO io.confluent.developer.AvroConsumer - Consumed event: key = 4, deviceId = 4, temperature = 91.70332
  [main] INFO io.confluent.developer.AvroConsumer - Consumed event: key = 1, deviceId = 1, temperature = 85.44849
  [main] INFO io.confluent.developer.AvroConsumer - Consumed event: key = 3, deviceId = 3, temperature = 83.115486
  [main] INFO io.confluent.developer.AvroConsumer - Consumed event: key = 1, deviceId = 1, temperature = 80.96756
  [main] INFO io.confluent.developer.AvroConsumer - Consumed event: key = 0, deviceId = 0, temperature = 90.369835
  ```

  Enter `Ctrl+C` to exit the application.

  ## Evolve the schema

  Let's evolve the temperature reading schema in a backward compatible way by adding an optional `factoryId` field, and also widening the type of the `temperature` field from a 4-byte `float` to an 8-byte `double`. These changes are captured in the `./src/main/avro/temp-reading-v2.avsc` schema file. Refer to [this table](https://docs.confluent.io/platform/current/schema-registry/fundamentals/schema-evolution.html#summary) for details on which schema changes are allowed for a given compatibility type and schema format.

  Next, review the modified `V2` applications that work with the updated schema. The producer (`AvroProducerV2`) now populates the `factoryId` field:

  ```java
  String factoryId = String.valueOf(rand.nextInt(5));
  ...
  GenericRecord reading = new GenericData.Record(schema);
  ...
  reading.put("factoryId", factoryId);
  ```

  Similarly, the consumer (`AvroConsumerV2`) is updated to log the factory ID to the console, and it handles both float and double temperatures:

  ```java
  double temp = ((Number)value.get("temperature")).doubleValue();
  ```

  Note that, because Avro `GenericRecord` doesn't offer type safety, developers must ensure that the applications using this API evolve in a backward compatible way. As an example of a non-backward compatible change, the expression `(Double)value.get("temperature");` would trigger an exception if run against records produced by `AvroProducer` because a boxed `Float` doesn't automatically get re-boxed to a `Double`. Attempting to cast a `Float` to a `Double` triggers a `RuntimeException` with message `class java.lang.Float cannot be cast to class java.lang.Double`.

  ## Run updated applications

  Since our [schema compatibility type](https://docs.confluent.io/platform/current/schema-registry/fundamentals/schema-evolution.html#compatibility-types) is the default `BACKWARD`, consumers using the new schema can read data produced with the previous schema. Conversely, there is no guarantee that a consumer using the first version of the schema will work with data produced with the updated version. Therefore, we must upgrade consumers _before_ upgrading producers.

  Start the upgraded consumer and leave it running:

  ```shell
  java -cp ./build/libs/schema-evolution-standalone.jar \
      io.confluent.developer.AvroConsumerV2 \
      ./src/main/resources/local.properties
  ```

  Run the upgraded producer in another terminal:

  ```shell
  java -cp ./build/libs/schema-evolution-standalone.jar \
      io.confluent.developer.AvroProducerV2 \
      ./src/main/resources/local.properties \
      ./src/main/avro/temp-reading-v2.avsc
  ```

  Run the original producer again to observe that the new consumer works with it:

  ```shell
  java -cp ./build/libs/schema-evolution-standalone.jar \
      io.confluent.developer.AvroProducer \
      ./src/main/resources/local.properties \
      ./src/main/avro/temp-reading.avsc
  ```

  You will see that the upgraded consumer properly handles messages adhering to the original and updated schema:

  ```plaintext
  [main] INFO Consumed event: key = 2, deviceId = 2, factoryId = 2, temperature = 75.96076296305301 (type class java.lang.Double)
  [main] INFO Consumed event: key = 1, deviceId = 1, factoryId = 3, temperature = 83.7503375910799 (type class java.lang.Double)
  ...
  [main] INFO Consumed event: key = 1, deviceId = 1, factoryId = N/A, temperature = 85.32722473144531 (type class java.lang.Float)
  [main] INFO Consumed event: key = 0, deviceId = 0, factoryId = N/A, temperature = 89.28128051757812 (type class java.lang.Float)
  ```

  ## Clean up

  From your local machine, stop the broker and Schema Registry containers. From the top-level `tutorials` directory:

  ```shell
  docker compose -f ./docker/docker-compose-kafka-sr.yml down
  ```

</details>
