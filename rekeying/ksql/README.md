<!-- title: How to rekey a stream with ksqlDB -->
<!-- description: In this tutorial, learn how to rekey a stream with ksqlDB, with step-by-step instructions and supporting code. -->

# How to rekey a stream with ksqlDB

If you have a stream that is either unkeyed (the key is null) or not keyed on the desired field, you can rekey the stream
by issuing a [`CREATE STREAM AS SELECT`](https://docs.ksqldb.io/en/latest/developer-guide/ksqldb-reference/create-stream-as-select/)
(CSAS) statement and explicitly specifying the new key with `PARTITION BY`. The new stream can be partitioned by a value or a scalar function.

For example, suppose that you have an unkeyed stream representing movies:

```sql
CREATE STREAM movies (id INT, title VARCHAR, genre VARCHAR)
    WITH (KAFKA_TOPIC='movies',
          PARTITIONS=1,
          VALUE_FORMAT='AVRO');
```

Assume that the `title` field includes both the title and release year, e.g.:

```sql
INSERT INTO movies (id, title, genre) VALUES (294, 'Die Hard::1988', 'action');
```

Then you can rekey by a value (e.g., the `id` field) as follows:

```sql
CREATE STREAM movies_by_id
    WITH (KAFKA_TOPIC='movies_by_id') AS
SELECT *
FROM movies
    PARTITION BY id;
```

Or, you can rekey by the result of a scalar function (e.g., the title extracted from the `title` field) as follows:

```sql
CREATE STREAM movies_by_title
    WITH (KAFKA_TOPIC='movies_by_title') AS
        SELECT *
        FROM movies
        PARTITION BY SPLIT(title, '::')[1];
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

  Run the following SQL statements to create the `movies` stream backed by Kafka running in Docker and populate it with
  test data.

  ```sql
  CREATE STREAM movies (id INT, title VARCHAR, genre VARCHAR)
      WITH (KAFKA_TOPIC='movies',
            PARTITIONS=1,
            VALUE_FORMAT='AVRO');
  ```

  ```sql
  INSERT INTO movies (id, title, genre) VALUES (1, 'Twisters::2024', 'drama');
  INSERT INTO movies (id, title, genre) VALUES (2, 'Unfrosted::2024', 'comedy');
  INSERT INTO movies (id, title, genre) VALUES (3, 'Family Switch::2023', 'comedy');
  ```

  Next, run the following `CREATE STREAM AS SELECT` statements to create new rekeyed streams. The first rekeys by a
  value (the `id` field), and the second rekeys by the result of the `SPLIT` scalar function. Note that we first tell 
  ksqlDB to consume from the beginning of the stream.

  ```sql
  SET 'auto.offset.reset'='earliest';

  CREATE STREAM movies_by_id
      WITH (KAFKA_TOPIC='movies_by_id') AS
  SELECT *
  FROM movies
  PARTITION BY id;
  ```

  ```sql
  CREATE STREAM movies_by_title
      WITH (KAFKA_TOPIC='movies_by_title') AS
          SELECT *
          FROM movies
          PARTITION BY SPLIT(title, '::')[1];
  ```

  If you run the following `PRINT` query, you can see that the key of the `movies_by_title` stream is the expected title:

  ```sql
  PRINT movies_by_title;
  ```

  The result will include the expected title keys:

  ```plaintext
  key: Twisters
  key: Unfrosted
  key: Family Switch
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

  Enter the following statements in the editor and click `Run query`. This creates the `movies` stream and
  populates it with test data.

  ```sql
  CREATE STREAM movies (id INT, title VARCHAR, genre VARCHAR)
      WITH (KAFKA_TOPIC='movies',
            PARTITIONS=1,
            VALUE_FORMAT='AVRO');

  INSERT INTO movies (id, title, genre) VALUES (1, 'Twisters::2024', 'drama');
  INSERT INTO movies (id, title, genre) VALUES (2, 'Unfrosted::2024', 'comedy');
  INSERT INTO movies (id, title, genre) VALUES (3, 'Family Switch::2023', 'comedy');
  ```

  Next, create new rekeyed streams. The first is rekeyed by a value (the `id` field), and the second rekeys by the 
  result of the `SPLIT` scalar function. Paste these queries in the editor and click `Run query`.

  ```sql
  CREATE STREAM movies_by_id
      WITH (KAFKA_TOPIC='movies_by_id') AS
  SELECT *
  FROM movies
  PARTITION BY id;
  ```

  ```sql
  CREATE STREAM movies_by_title
      WITH (KAFKA_TOPIC='movies_by_title') AS
          SELECT *
          FROM movies
          PARTITION BY SPLIT(title, '::')[1];
  ```

  If you run the following `DESCRIBE` query, you can see that the key of the `movies_by_title` stream is the expected title:

  ```sql
  DESCRIBE movies_by_title;
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
