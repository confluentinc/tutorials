<!-- title: How to update the number of partitions of a Kafka topic with ksqlDB -->
<!-- description: In this tutorial, learn how to update the number of partitions of a Kafka topic with ksqlDB. -->

# How to update the number of partitions of a Kafka topic with ksqlDB

Imagine you want to change the partitions of your Kafka topic. You can use a streaming transformation to automatically stream all the messages from the original topic into a new Kafka topic that has the desired number of partitions.

## Setup

To accomplish this transformation, first create a stream based on the original topic:

```sql
CREATE STREAM s1 (k VARCHAR KEY, v VARCHAR)
    WITH (KAFKA_TOPIC='topic',
          VALUE_FORMAT='JSON');
```

Then, create a second stream that reads everything from the original topic and puts into a new topic with the desired number of partitions:

```sql
CREATE STREAM s2
    WITH (KAFKA_TOPIC='topic2',
          VALUE_FORMAT='JSON',
          PARTITIONS=2) AS
    SELECT *
    FROM s1
    EMIT CHANGES;
```

## Running the example

You can run the example backing this tutorial in one of two ways: locally with the `ksql` CLI against Kafka and ksqlDB running in Docker, or with Confluent Cloud.

<details>
  <summary>Local With Docker</summary>

  ### Prerequisites

  * Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
  * [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.

  ### Run the commands

  Clone the `confluentinc/tutorials` GitHub repository (if you haven't already) and navigate to the `tutorials` directory:

  ```shell
  git clone git@github.com:confluentinc/tutorials.git
  cd tutorials
  ```

  Start ksqlDB and Kafka:

  ```shell
  docker compose -f ./docker/docker-compose-ksqldb.yml up -d
  ```

  Next, open the ksqlDB CLI:

  ```shell
  docker exec -it ksqldb-cli ksql http://ksqldb-server:8088
  ```

  Run the following SQL statements to create the `s1` stream backed by Kafka running in Docker and populate it with test data.

  ```sql
  CREATE STREAM s1 (k VARCHAR KEY, v VARCHAR)
      WITH (KAFKA_TOPIC='topic',
            PARTITIONS=1,
            VALUE_FORMAT='JSON');
  ```

  ```sql
  INSERT INTO s1 (k, v) VALUES ('hello', 'world');
  INSERT INTO s1 (k, v) VALUES ('foo', 'bar');
  INSERT INTO s1 (k, v) VALUES ('bar', 'baz');
  ```

  Next, run the `CREATE STREAM AS SELECT` query to populate a new topic, `topic2` with the same
  events in `topic` but having 2 partitions.

  ```sql
  SET 'auto.offset.reset'='earliest';

  CREATE STREAM s2
      WITH (KAFKA_TOPIC='topic2',
            VALUE_FORMAT='JSON',
            PARTITIONS=2) AS
      SELECT *
      FROM s1
      EMIT CHANGES;
  ```

  Observe the expected number of partitions when you run the `kafka-topics` command in the broker container:

  ```shell
  docker exec -it broker kafka-topics --bootstrap-server localhost:29092 --describe --topic topic1
  ```

  ```shell
  docker exec -it broker kafka-topics --bootstrap-server localhost:29092 --describe --topic topic2
  ```

  When you are finished, exit the ksqlDB CLI by entering `CTRL-D` and clean up the containers used for this tutorial by running:

  ```shell
  docker compose -f ./docker/docker-compose-ksqldb.yml down
  ```

</details>

<details>
  <summary>Confluent Cloud</summary>

  ### Prerequisites

  * A [Confluent Cloud](https://confluent.cloud/signup) account
  * The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine

  ### Create Confluent Cloud resources

  Login to your Confluent Cloud account:

  ```shell
  confluent login --prompt --save
  ```

  Install a CLI plugin that will streamline the creation of resources in Confluent Cloud:

  ```shell
  confluent plugin install confluent-cloud_kickstart
  ```

  Run the following command to create a Confluent Cloud environment and Kafka cluster. This will create 
  resources in AWS region `us-west-2` by default, but you may override these choices by passing the `--cloud` argument with
  a value of `aws`, `gcp`, or `azure`, and the `--region` argument that is one of the cloud provider's supported regions,
  which you can list by running `confluent kafka region list --cloud <CLOUD PROVIDER>`
  
  ```shell
  confluent cloud-kickstart --name ksqldb-tutorial \
    --environment-name ksqldb-tutorial \
    --output-format stdout
  ```

  Now, create a ksqlDB cluster by first getting your user ID of the form `u-123456` when you run this command:

  ```shell
  confluent iam user list
  ```

  And then create a ksqlDB cluster called `ksqldb-tutorial` with access linked to your user account:

  ```shell
  confluent ksql cluster create ksqldb-tutorial \
    --credential-identity <USER ID>
  ```

  ### Run the commands

  Login to the [Confluent Cloud Console](https://confluent.cloud/). Select `Environments` in the lefthand navigation,
  and then click the `ksqldb-tutorial` environment tile. Click the `ksqldb-tutorial` Kafka cluster tile, and then
  select `ksqlDB` in the lefthand navigation.

  The cluster may take a few minutes to be provisioned. Once its status is `Up`, click the cluster name and scroll down to the editor.

  In the query properties section at the bottom, change the value for `auto.offset.reset` to `Earliest` so that ksqlDB 
  will consume from the beginning of the stream we create.

  Enter the following statements in the editor and click `Run query`. This creates the `s1` stream and
  populates it with test data.


  ```sql
  CREATE STREAM s1 (k VARCHAR KEY, v VARCHAR)
      WITH (KAFKA_TOPIC='topic',
            PARTITIONS=1,
            VALUE_FORMAT='JSON');

  INSERT INTO s1 (k, v) VALUES ('hello', 'world');
  INSERT INTO s1 (k, v) VALUES ('foo', 'bar');
  INSERT INTO s1 (k, v) VALUES ('bar', 'baz');
  ```

  Now paste the `CREATE STREAM AS SELECT` query to populate a new topic, `topic2` with the same
  events in `topic` but having 2 partitions.

  ```sql
  CREATE STREAM s2
      WITH (KAFKA_TOPIC='topic2',
            VALUE_FORMAT='JSON',
            PARTITIONS=2) AS
      SELECT *
      FROM s1
      EMIT CHANGES;
  ```

  Observe the expected number of partitions for the `topic` and `topic2` topics when you navigate
  to `Topics` in the lefthand navigation of the Confluent Cloud Console.

  ### Clean up

  When you are finished, delete the `ksqldb-tutorial` environment by first getting the environment ID of the form 
  `env-123456` corresponding to it:

  ```shell
  confluent environment list
  ```

  Delete the environment, including all resources created for this tutorial:

  ```shell
  confluent environment delete <ENVIRONMENT ID>
  ```

</details>
