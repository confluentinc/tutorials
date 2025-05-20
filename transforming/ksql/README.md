<!-- title: How to transform events with ksqlDB scalar functions -->
<!-- description: In this tutorial, learn how to transform events with ksqlDB scalar functions, with step-by-step instructions and supporting code. -->

# How to transform events with ksqlDB scalar functions

If you have a stream of events in a Kafka topic and wish to transform a field in each event, you an use ksqlDB's [scalar functions](https://docs.ksqldb.io/en/latest/developer-guide/ksqldb-reference/scalar-functions/) or implement your own [scalar UDF](https://docs.ksqldb.io/en/latest/how-to-guides/create-a-user-defined-function/#scalar-functions) if
your needs aren't met by the built-in functions.

## Setup

As a concrete example, consider a stream containing events that represent movies. 

```sql
CREATE STREAM movies (id INT KEY, title VARCHAR, genre VARCHAR)
    WITH (KAFKA_TOPIC='movies',
          PARTITIONS=1,
          VALUE_FORMAT='AVRO');
```

Each event has a `title` attribute that combines its title and its release year into a string, e.g., `Inside Out 2::2024`.

## Transform events

Given the stream of movies, we can break the `title` field into separate attributes for the title and release year using the
[SPLIT](https://docs.ksqldb.io/en/latest/developer-guide/ksqldb-reference/scalar-functions/#split) function. `CAST` is
also used to convert the resulting release year's data type from string to integer.

```sql
SELECT id,
       SPLIT(title, '::')[1] AS title,
       CAST(SPLIT(title, '::')[2] AS INT) AS year,
       genre
FROM movies
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

  Run the following SQL statements to create the `movies` stream backed by Kafka running in Docker and populate it with
  test data.

  ```sql
  CREATE STREAM movies (id INT KEY, title VARCHAR, genre VARCHAR)
      WITH (KAFKA_TOPIC='movies',
            PARTITIONS=1,
            VALUE_FORMAT='AVRO');
  ```

  ```sql
  INSERT INTO movies (id, title, genre) VALUES (1, 'Twisters::2024', 'drama');
  INSERT INTO movies (id, title, genre) VALUES (2, 'Unfrosted::2024', 'comedy');
  INSERT INTO movies (id, title, genre) VALUES (3, 'Family Switch::2023', 'comedy');
  ```

  Next, run the event transformation query to split the `title` field into the actual movie title and release year. Note that we 
  first tell ksqlDB to consume from the beginning of the stream.

  ```sql
  SET 'auto.offset.reset'='earliest';

  SELECT id,
         SPLIT(title, '::')[1] AS title,
         CAST(SPLIT(title, '::')[2] AS INT) AS year,
         genre
  FROM movies
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +---------------------+---------------------+---------------------+---------------------+
  |ID                   |TITLE                |YEAR                 |GENRE                |
  +---------------------+---------------------+---------------------+---------------------+
  |1                    |Twisters             |2024                 |drama                |
  |2                    |Unfrosted            |2024                 |comedy               |
  |3                    |Family Switch        |2023                 |comedy               |
  +---------------------+---------------------+---------------------+---------------------+
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
  CREATE STREAM movies (id INT KEY, title VARCHAR, genre VARCHAR)
      WITH (KAFKA_TOPIC='movies',
            PARTITIONS=1,
            VALUE_FORMAT='AVRO');

  INSERT INTO movies (id, title, genre) VALUES (1, 'Twisters::2024', 'drama');
  INSERT INTO movies (id, title, genre) VALUES (2, 'Unfrosted::2024', 'comedy');
  INSERT INTO movies (id, title, genre) VALUES (3, 'Family Switch::2023', 'comedy');
  ```

  Now paste the the event transformation query to split the `title` field into the actual movie title and release year, 
  and click `Run query`:

  ```sql
  SELECT id,
         SPLIT(title, '::')[1] AS title,
         CAST(SPLIT(title, '::')[2] AS INT) AS year,
         genre
  FROM movies
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +---------------------+---------------------+---------------------+---------------------+
  |ID                   |TITLE                |YEAR                 |GENRE                |
  +---------------------+---------------------+---------------------+---------------------+
  |1                    |Twisters             |2024                 |drama                |
  |2                    |Unfrosted            |2024                 |comedy               |
  |3                    |Family Switch        |2023                 |comedy               |
  +---------------------+---------------------+---------------------+---------------------+
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
