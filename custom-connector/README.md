<!-- title: How to write and deploy a custom connector on Confluent Cloud -->
<!-- description: In this tutorial, learn how to write and deploy a custom connector on Confluent Cloud, with step-by-step instructions and supporting code. -->

# How to write and deploy a custom connector on Confluent Cloud

This project provides a barebones example of a Kafka Connect source connector that emits events containing incrementing numbers. It includes a unit test, provides a couple of basic configurations, and manages offsets to demonstrate how to handle task restarts gracefully. Use it as a jumping off point and massage it into a more interesting, real-world connector.

### Prerequisites

* A [Confluent Cloud](https://www.confluent.io/confluent-cloud/tryfree) account
* The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
* Clone the `confluentinc/tutorials` GitHub repository (if you haven't already) and navigate to the `tutorials` directory:
```shell
git clone git@github.com:confluentinc/tutorials.git
cd tutorials
```

### Develop the custom connector

This example uses Gradle to compile and package the connector. Most of the `settings.gradle` and `build.gradle` files are boilerplate, but notice two important sections:

1. A dependency on the Kafka Connect API, `org.apache.kafka:connect-api`
2. The Gradle Shadow plugin that is used to create an uber JAR (i.e., a JAR containing dependencies) 

You can run the example backing this tutorial locally in Docker, or with Confluent Cloud.

Take a look at the three classes under `src/main/java/io/confluent/developer`.

`CounterConnectorConfig` is configuration class defines the two configuration properties for our connector: the topic to write to (`kafka.topic`), and the number of milliseconds to wait between emitting events (`interval.ms`).

`CounterConnector` is the glue between the connector configuration and the task implementation. We’ll provide the fully qualified name of this class (`io.confluent.developer.connect.CounterConnector`) later in this tutorial when adding the connector plugin to Confluent Cloud.

`CounterSourceTask` actually generates numeric events in the `poll()` method.

### Test the custom connector

Run the tests in `src/test/java/io/confluent/developer/CounterSourceTaskTest`:
```shell
./gradlew clean :custom-connector:test
```
This class contains a [JUnit 5 parametrized test](https://junit.org/junit5/docs/current/user-guide/#writing-tests-parameterized-tests) that tests the connect task for correctness in a few different scenarios.

### Package the custom connector

Run the follow command to build an uber JAR:
```shell
gradle shadowJar
```
This command generates the JAR `build/libs/kafka-connect-counter.jar`, which we will upload to Confluent Cloud in the next section.

### Deploy the custom connector on Confluent Cloud

To run the connector in Confluent Cloud:

1. Login to Confluent Cloud:
```shell
confluent login --prompt --save
```

2. Create a temporary environment to use. This will make cleanup easier:
```shell
confluent environment create source-connector-env
```

3. Set the environment as the active environment by providing the environment ID of the form `env-123456` in the following command:
```shell
confluent environment use <ENVIRONMENT ID>
```

4. Create a Basic Kafka cluster by entering the following command, where `<provider>` is one of `aws`, `azure`, or `gcp`, and `<region>` is a region ID available in the cloud provider you choose. You can view the available regions for a given cloud provider by running `confluent kafka region list --cloud <provider>`. For example, to create a cluster in AWS region `us-east-1`:
```shell
confluent kafka cluster create quickstart --cloud aws --region us-east-1
```

5. It may take a few minutes for the cluster to be created. Validate that the cluster is running by ensuring that its `Status` is `Up` when you run the following command:
```shell
confluent kafka cluster list
```

6. Set the cluster as the active cluster by providing the cluster ID of the form `lkc-123456` in the following command:
```shell
confluent kafka cluster use <CLUSTER ID>
```

7. Upload the connector plugin to Confluent Cloud in the cloud where your cluster resides. For example, if you are using AWS:
```shell
confluent connect custom-plugin create counter-source --plugin-file build/libs/kafka-connect-counter.jar --connector-type source --connector-class io.confluent.developer.CounterConnector --cloud aws
```
Note the ID of the form `ccp-123456` that this command outputs.

8. Now that the plugin is uploaded, we'll instantiate a connector. First create an API key. Get the ID of your cluster of the form `lkc-123456` by running `confluent kafka cluster list`. Then, create an API key that your connector will use to access it:
```shell
confluent api-key create --resource <CLUSTER ID>
```

9. Create a connector configuration file `/tmp/counter-source.json` using the API key and secret generated in the previous step, as well as the plugin ID of the form `ccp-123456` output in step 4:
```json
{
  "name": "CounterSource",
  "config": {
    "connector.class": "io.confluent.developer.CounterConnector",
    "kafka.auth.mode": "KAFKA_API_KEY",
    "kafka.api.key": "<API KEY>>",
    "kafka.api.secret": "<API SECRET>",
    "tasks.max": "1",
    "confluent.custom.plugin.id": "<PLUGIN ID>",
    "confluent.connector.type": "CUSTOM",
    "interval.ms": "1000",
    "kafka.topic": "counts"
  }
}
```

10. Provision the connector:
```shell
confluent connect cluster create --config-file /tmp/counter-source.json
```

11. Delete the connector configuration file:
```shell
rm -f /tmp/counter-source.json
```

12. Once the connector is provisioned, you will be able to consume from the `counts` topic. First, store the API key from before so that the CLI can use it to connect:
```shell
confluent api-key use <API KEY>
```
Then, consume from the `counts` topic:
```shell
confluent kafka topic consume counts --from-beginning
```
You will see an incrementing integer output every second. Enter `Ctrl+C` to exit the console consumer.

13. To clean up, delete the `source-connector-env` environment. Get your environment ID of the form `env-123456` by running `confluent environment list` and then run:
```shell
confluent environment delete <ENVIRONMENT ID>
```
