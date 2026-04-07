<!-- title: How to validate and cleanse events with Schema Registry rules -->
<!-- description: In this tutorial, learn how to validate and cleanse events with Schema Registry rules, with step-by-step instructions and supporting code. -->

# How to validate and cleanse events with Schema Registry rules

This tutorial demonstrates how to use Confluent Schema Registry's data quality rules and field-level transforms to ensure streaming data quality and consistency. You'll learn how to create validation rules that reject invalid data, route failed messages to a dead letter queue, and apply field transformations to cleanse data before it reaches your topics. These capabilities help you enforce data contracts and maintain high-quality event streams without adding validation logic to your producers and consumers.

The following steps use Confluent Cloud. To run the tutorial locally with Confluent Platform running in Docker, skip to the Docker instructions section at the bottom.

## Prerequisites

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

Run the plugin from the top-level directory of the `tutorials` repository to create the Confluent Cloud resources needed for this tutorial. Note that you may specify a different cloud provider (`gcp` or `azure`) or region. You can find supported regions in a given cloud provider by running `confluent kafka region list --cloud <CLOUD>`.

```shell
confluent quickstart \
  --environment-name kafka-sr-rules-env \
  --kafka-cluster-name kafka-sr-rules-cluster \
  --create-kafka-key \
  --kafka-java-properties-file ./schema-registry-rules-validation/cloud-kafka.properties \
  --create-sr-key
```

The plugin should complete in under a minute.

Once it completes, enable the Advanced Stream Governance package that includes rules support. First, get its environment ID (of the form `env-123456`):

```shell
confluent environment list
```

Enable Advanced governance:

```shell
confluent environment update <ENVIRONMENT ID> \
  --governance-package advanced
```

## Create topics

Create the topic used in this tutorial:

```shell
confluent kafka topic create transactions
confluent kafka topic create dlq-topic
```

## Register schemas

Navigate into the tutorial home directory:

```shell
cd schema-registry-rules-validation/confluent-cloud
```

Register the three schemas with Schema Registry:

```shell
confluent schema-registry schema create --subject transactions-value \
  --type json \
  --ruleset ruleset-error-on-failure.json \
  --schema schema.json

confluent schema-registry schema create --subject transactions-value \
  --type json \
  --ruleset ruleset-dlq-on-failure.json \
  --schema schema.json

confluent schema-registry schema create --subject transactions-value \
  --type json \
  --ruleset ruleset-with-field-transform.json \
  --schema schema.json
```

Make note of the IDs that are returned as we will use them in the following steps.

## Produce with error on failure

Run a console producer to produce messages using the first value schema ID (error on account ID length not being 8 characters).

```shell
confluent kafka topic produce transactions --schema <SCHEMA ID>
```

Enter a valid transaction with an 8-character account ID followed by an invalid transaction with a 9-character account ID:

```plaintext
{"account_id": "12345678", "amount": 481.52}
{"account_id": "123456789", "amount": 20.00}
```

Observe that the first message is produced successfully while the second fails with an error `ERROR: rule checkIdLen failed`. Double check that the first message can be consumed:

```shell
confluent kafka topic consume transactions --from-beginning
```

## Produce with dead letter queue on failure

Run a console producer to produce messages using the second value schema ID (route failure messages to dead letter queue topic `dlq-topic`).

```shell
confluent kafka topic produce transactions --schema <SCHEMA ID>
```

Enter an invalid transaction with a 9-character account ID:

```plaintext
{"account_id": "abcdefghi", "amount": 1.99}
```

Observe that the message fails with an error `Rule failed: checkIdLen`. This time, however, the failed message winds up in the dead letter queue. Check the DLQ topic for the invalid message:

```shell
confluent kafka topic consume dlq-topic --from-beginning
```

Observe the failed message:

```plaintext
{"account_id":"abcdefghi","amount":1.99}
```

## Produce with field transformation

In addition to `CONDITION` rules that enforce data quality, Confluent also supports `TRANSFORM` rules to clean up data. The third schema in this tutorial defines a transform to collapse whitespace and hyphens in the `account_id` field:

```noformat
"expr": "name == 'account_id' ; value.replace(' ', '').replace('-', '')"
```

To test the transform, run a console producer to produce messages using the third value schema ID (first collapse whitespace and hyphens, and then route to DLQ if cleaned account ID field isn't 8 characters).

```shell
confluent kafka topic produce transactions --schema <SCHEMA ID>
```

Enter a transaction with an account ID that includes hyphens and whitespace:

```plaintext
{"account_id": "1234-5678 ", "amount": 12.16}
```

Now run the consumer:

```shell
confluent kafka topic consume transactions --from-beginning
```

Observe the message with the cleaned account ID:

```plaintext
{"amount":12.16,"account_id":"12345678"}
```

## Clean up

When you're finished, delete the `kafka-sr-rules-env` environment. First, get its environment ID (of the form `env-123456`):

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

  ## Start Kafka and Schema Registry in Docker

  Start Kafka and Schema Registry with the following command from the top-level `tutorials` repository:

  ```shell
  docker compose -f ./docker/docker-compose-kafka-sr-cp.yml up -d
  ```

  ## Create topics

  ```shell
  docker exec -it broker /bin/bash
  ```

  ```shell
  kafka-topics --bootstrap-server localhost:9092 --create --topic transactions
  kafka-topics --bootstrap-server localhost:9092 --create --topic dlq-topic
  ```

  Enter `Ctrl+D` to exit the broker container.

  ## Register schemas

  ```shell
  cd schema-registry-rules-validation/confluent-platform/

  curl -X POST http://localhost:8081/subjects/transactions-value/versions \
    -H "Content-Type: application/vnd.schemaregistry.v1+json" \
    -d '@schema-error-on-failure.json'

  curl -X POST http://localhost:8081/subjects/transactions-value/versions \
    -H "Content-Type: application/vnd.schemaregistry.v1+json" \
    -d '@schema-dlq-on-failure.json'

  curl -X POST http://localhost:8081/subjects/transactions-value/versions \
    -H "Content-Type: application/vnd.schemaregistry.v1+json" \
    -d '@schema-with-field-transform.json'
  ```  

  ## Produce with error on failure

  Open a shell in the Schema Registry container:

  ```shell
  docker exec -it schema-registry /bin/bash
  ```

  Run a console producer to produce messages using value schema ID 1 (error on account ID length not being 8 characters).

  ```shell
  kafka-json-schema-console-producer \
    --topic transactions \
    --bootstrap-server broker:29092 \
    --property bootstrap.servers=broker:29092 \
    --property schema.registry.url=http://schema-registry:8081 \
    --property value.schema.id=1
  ```

  Enter a valid transaction with an 8-character account ID followed by an invalid transaction with a 9-character account ID:

  ```plaintext
  {"account_id": "12345678", "amount": 481.52}
  {"account_id": "123456789", "amount": 20.00}
  ```

  Observe that the first message is produced successfully while the second fails with an error `Rule failed: checkIdLen`. Double check that the first message can be consumed:

  ```shell
  kafka-json-schema-console-consumer \
      --topic transactions \
      --from-beginning \
      --bootstrap-server broker:29092 \
      --property schema.registry.url=http://schema-registry:8081
  ```

  ## Produce with dead letter queue on failure

  In the Schema Registry container, run a console producer to produce messages using value schema ID 2 (route failure messages to dead letter queue topic `dlq-topic`).

  ```shell
  kafka-json-schema-console-producer \
    --topic transactions \
    --bootstrap-server broker:29092 \
    --property bootstrap.servers=broker:29092 \
    --property schema.registry.url=http://schema-registry:8081 \
    --property value.schema.id=2
  ```

  Enter an invalid transaction with a 9-character account ID:

  ```plaintext
  {"account_id": "abcdefghi", "amount": 1.99}
  ```

  Observe that the message fails with an error `Rule failed: checkIdLen`. This time, however, the failed message winds up in the dead letter queue. Open a shell in the broker container:

  ```shell
  docker exec -it broker /bin/bash
  ```
    
  Consume from the beginning of `dlq-topic`:

  ```shell
  kafka-console-consumer \
    --topic dlq-topic \
    --from-beginning \
    --bootstrap-server broker:29092
  ```

  Observe the failed message:

  ```plaintext
  {"account_id":"abcdefghi","amount":1.99}
  ```

  ## Produce with field transformation

  In addition to `CONDITION` rules that enforce data quality, Confluent also supports `TRANSFORM` rules to clean up data. The third schema in this tutorial defines a transform to collapse whitespace and hyphens in the `account_id` field:

  ```noformat
  "expr": "name == 'account_id' ; value.replace(' ', '').replace('-', '')"
  ```

  To test the transform, run a console producer in the Schema Registry container to produce messages using value schema ID 3 (first collapse whitespace and hyphens, and then route to DLQ if cleaned account ID field isn't 8 characters).

  ```shell
  kafka-json-schema-console-producer \
    --topic transactions \
    --bootstrap-server broker:29092 \
    --property bootstrap.servers=broker:29092 \
    --property schema.registry.url=http://schema-registry:8081 \
    --property value.schema.id=3
  ```

  Enter a transaction with an account ID that includes hyphens and whitespace:

  ```plaintext
  {"account_id": "1234-5678 ", "amount": 12.16}
  ```

  Now run the consumer:

  ```shell
  kafka-json-schema-console-consumer \
      --topic transactions \
      --from-beginning \
      --bootstrap-server broker:29092 \
      --property schema.registry.url=http://schema-registry:8081
  ```

  Observe the message with the cleaned account ID:

  ```plaintext
  {"amount":12.16,"account_id":"12345678"}
  ```

  ## Clean up

  From your local machine, stop the broker and Schema Registry containers. From the top-level `tutorials` directory:

  ```shell
  docker compose -f ./docker/docker-compose-kafka-sr-cp.yml down
  ```

</details>
