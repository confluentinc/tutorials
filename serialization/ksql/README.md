<!-- title: How to change the serialization format of messages with ksqlDB -->
<!-- description: In this tutorial, learn how to change the serialization format of messages with ksqlDB, with step-by-step instructions and supporting code. -->

# How to change the serialization format of messages with ksqlDB

If you have a stream of Avro-formatted events in a Kafka topic, it's trivial to convert the events to Protobuf format by using a [CREATE STREAM AS SELECT](https://docs.ksqldb.io/en/latest/developer-guide/ksqldb-reference/create-stream-as-select/) (CSAS) statement to populate the Protobuf-formatted stream with values from the Avro-formatted stream.

For example, suppose that you have a stream with Avro-formatted values that represent movie releases:

```sql
CREATE STREAM movies_avro (movie_id BIGINT KEY, title VARCHAR, release_year INT)
    WITH (KAFKA_TOPIC='movies_avro',
          PARTITIONS=1,
          VALUE_FORMAT='AVRO');
```

Then the analogous stream with Protobuf-formatted values can be created and populated as follows:

```sql
CREATE STREAM movies_proto
    WITH (KAFKA_TOPIC='movies_proto', VALUE_FORMAT='PROTOBUF') AS
    SELECT * FROM movies_avro;
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

  Run the following SQL statements to create the `movies_avro` stream backed by Kafka running in Docker and populate it with
  test data.

  ```sql
  CREATE STREAM movies_avro (movie_id BIGINT KEY, title VARCHAR, release_year INT)
      WITH (KAFKA_TOPIC='movies_avro',
            PARTITIONS=1,
            VALUE_FORMAT='AVRO');
  ```

  ```sql
  INSERT INTO movies_avro (movie_id, title, release_year) VALUES (1, 'Lethal Weapon', 1992);
  INSERT INTO movies_avro (movie_id, title, release_year) VALUES (2, 'The Batman', 2022);
  INSERT INTO movies_avro (movie_id, title, release_year) VALUES (3, 'Beetlejuice Beetlejuice', 2024);
  ```

  Finally, run the `CREATE STREAM AS SELECT` query to create an analogous stream with Protobuf-formatted values.
  Note that we first tell ksqlDB to consume from the beginning of the stream.

  ```sql
  SET 'auto.offset.reset'='earliest';

  CREATE STREAM movies_proto
      WITH (KAFKA_TOPIC='movies_proto', VALUE_FORMAT='PROTOBUF') AS
      SELECT * FROM movies_avro;
  ```

  Query the new topic and observe the same events as in the source topic:

  ```sql
  SELECT *
  FROM movies_proto;
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

  Login to the [Confluent Cloud Console](https://confluent.cloud/). Select `Environments` in the left-hand navigation,
  and then click the `ksqldb-tutorial` environment tile. Click the `ksqldb-tutorial` Kafka cluster tile, and then
  select `ksqlDB` in the left-hand navigation.

  The cluster may take a few minutes to be provisioned. Once its status is `Up`, click the cluster name and scroll down to the editor.

  In the query properties section at the bottom, change the value for `auto.offset.reset` to `Earliest` so that ksqlDB 
  will consume from the beginning of the stream we create.

  Enter the following statements in the editor and click `Run query`. This creates the `movies_avro` stream and
  populates it with test data.

  ```sql
  CREATE STREAM movies_avro (movie_id BIGINT KEY, title VARCHAR, release_year INT)
      WITH (KAFKA_TOPIC='movies_avro',
            PARTITIONS=1,
            VALUE_FORMAT='AVRO');

  INSERT INTO movies_avro (movie_id, title, release_year) VALUES (1, 'Lethal Weapon', 1992);
  INSERT INTO movies_avro (movie_id, title, release_year) VALUES (2, 'The Batman', 2022);
  INSERT INTO movies_avro (movie_id, title, release_year) VALUES (3, 'Beetlejuice Beetlejuice', 2024);
  ```

  Now paste the `CREATE STREAM AS SELECT` query in the editor and click `Run query`:

  ```sql
  CREATE STREAM movies_proto
      WITH (KAFKA_TOPIC='movies_proto', VALUE_FORMAT='PROTOBUF') AS
      SELECT * FROM movies_avro;
  ```

  Query the new topic and observe the same events as in the source topic:

  ```sql
  SELECT *
  FROM movies_proto;
  ```

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
