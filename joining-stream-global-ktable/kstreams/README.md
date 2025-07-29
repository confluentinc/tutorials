<!-- title: How to join a KStream and a GlobalKTable in Kafka Streams -->
<!-- description: In this tutorial, learn how to join a KStream and a GlobalKTable in Kafka Streams, with step-by-step instructions and supporting code. -->

# How to join a KStream and a GlobalKTable in Kafka Streams

In this tutorial, you'll learn how to join a `KStream` with a `GlobalKTable` in Kafka Streams. We'll demonstrate this by enriching an orders stream with product information from a products global table.

A `GlobalKTable` is replicated to all application instances, which means you can perform non-key based joins without needing to repartition the input stream. This is particularly useful when you have reference data (like a product catalog) that you want to join with streaming data (like orders).

## Prerequisites

- A [Confluent Cloud](https://confluent.cloud/signup) account
- The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
- Clone the `confluentinc/tutorials` repository and navigate into its top-level directory:
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
  --environment-name join-stream-global-ktable-env \
  --kafka-cluster-name join-stream-global-ktable-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./joining-stream-global-ktable/kstreams/cloud.properties
```

The plugin should complete in under a minute.

## Create topics

Create the input and output topics for the application:

```shell
confluent kafka topic create product-input
confluent kafka topic create orders-input
confluent kafka topic create enriched-orders-output
```

## Produce sample data

First, let's add some products to the products topic. Start a console producer for products:

```shell
confluent kafka topic produce product-input --parse-key --delimiter=":"
```

Enter the following product data (key:value format):

```json
1:{"productId":1,"productName":"sneakers"}
2:{"productId":2,"productName":"basketball"}
3:{"productId":3,"productName":"baseball"}
4:{"productId":4,"productName":"sweatshirt"}
5:{"productId":5,"productName":"tv"}
```

Enter `Ctrl+C` to exit the console producer.

Now, let's add some orders. Start a console producer for orders:

```shell
confluent kafka topic produce orders-input --parse-key --delimiter=":"
```

Enter the following order data:

```json
1:{"orderId":1,"productId":1}
2:{"orderId":2,"productId":3}
3:{"orderId":3,"productId":2}
4:{"orderId":4,"productId":5}
5:{"orderId":5,"productId":2}
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

Compile the application from the top-level `tutorials` repository directory:

```shell
./gradlew joining-stream-global-ktable:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd joining-stream-global-ktable/kstreams
```

Run the application, passing the Kafka client configuration file generated when you created Confluent Cloud resources:

```shell
java -cp ./build/libs/join-stream-to-global-ktable-standalone.jar \
    io.confluent.developer.JoinStreamToGlobalKTable \
    ./cloud.properties
```

## Validate the output

In a new terminal, validate that you see enriched orders in the output topic. The orders should now include product names:

```shell
confluent kafka topic consume enriched-orders-output -b
```

You should see enriched orders like this:

```json
{"orderId":1,"productId":1,"productName":"sneakers"}
{"orderId":2,"productId":3,"productName":"baseball"}
{"orderId":3,"productId":2,"productName":"basketball"}
{"orderId":4,"productId":5,"productName":"tv"}
{"orderId":5,"productId":2,"productName":"basketball"}
```

## Understanding the join

This example demonstrates a key advantage of a `GlobalKTable`:

1. **Non-key based join**: We join on `order.productId()` rather than the message key
2. **No repartitioning**: Since the `GlobalKTable` is replicated to all instances, we don't need to repartition the orders stream
3. **Reference data pattern**: Products act as reference data that enriches the streaming orders

The join function `(order, product) -> new EnrichedOrder(order.orderId(), product.productId(), product.productName())` creates the enriched output by combining data from both sources.

## Clean up

When you are finished, delete the `join-stream-global-ktable-env` environment by first getting the environment ID of the form `env-123456` corresponding to it:

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

- Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
- [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.
- Clone the `confluentinc/tutorials` repository and navigate into its top-level directory:
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
kafka-topics --bootstrap-server localhost:9092 --create --topic product-input
kafka-topics --bootstrap-server localhost:9092 --create --topic orders-input
kafka-topics --bootstrap-server localhost:9092 --create --topic enriched-orders-output
```

## Produce sample data

First, let's add products. Start a console producer for products:

```shell
kafka-console-producer --bootstrap-server localhost:9092 --topic product-input \
  --property "parse.key=true" --property "key.separator=:"
```

Enter the following product data:

```json
1:{"productId":1,"productName":"sneakers"}
2:{"productId":2,"productName":"basketball"}
3:{"productId":3,"productName":"baseball"}
4:{"productId":4,"productName":"sweatshirt"}
5:{"productId":5,"productName":"tv"}
```

Enter `Ctrl+C` to exit the console producer.

Now add orders. Start a console producer for orders:

```shell
kafka-console-producer --bootstrap-server localhost:9092 --topic orders-input \
  --property "parse.key=true" --property "key.separator=:"
```

Enter the following order data:

```json
1:{"orderId":1,"productId":1}
2:{"orderId":2,"productId":3}
3:{"orderId":3,"productId":2}
4:{"orderId":4,"productId":5}
5:{"orderId":5,"productId":2}
```

Enter `Ctrl+C` to exit the console producer.

## Compile and run the application

On your local machine, compile the app:

```shell
./gradlew joining-stream-global-ktable:kstreams:shadowJar
```

Navigate into the application's home directory:

```shell
cd joining-stream-global-ktable/kstreams
```

Run the application, passing the `local.properties` Kafka client configuration file that points to the broker's bootstrap servers endpoint at `localhost:9092`:

```shell
java -cp ./build/libs/join-stream-to-global-ktable-standalone.jar \
    io.confluent.developer.JoinStreamToGlobalKTable \
    ./local.properties
```

## Validate the output

In the broker container shell, validate that you see enriched orders in the output topic:

```shell
kafka-console-consumer --bootstrap-server localhost:9092 --topic enriched-orders-output --from-beginning
```

You should see enriched orders with product names included:

```plaintext
{"orderId":"1","productId":"1","productName":"sneakers"}
{"orderId":"2","productId":"3","productName":"baseball"}
{"orderId":"3","productId":"2","productName":"basketball"}
{"orderId":"4","productId":"5","productName":"tv"}
{"orderId":"5","productId":"2","productName":"basketball"}
```

## Clean up

From your local machine, stop the broker container:

```shell
docker compose -f ./docker/docker-compose-kafka.yml down
```

</details>
