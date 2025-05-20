<!-- title: How to join a stream and a table in ksqlDB -->
<!-- description: In this tutorial, learn how to join a stream and a table in ksqlDB, with step-by-step instructions and supporting code. -->

# How to join a stream and a table in ksqlDB

Suppose that you have events in a Kafka topic and a table of reference data (also known as a lookup table).
Let's see how you can join each event in the stream to attributes in the table based on a common key.

## Setup

Let's use the example of a movie rating event stream.  But the stream only contains the movie id, which isn't very
descriptive, so you want to enrich it with some additional information.  So you'll set up join between the stream and a table that contains fact or lookup data.

Here's the movie rating stream:

```sql
CREATE STREAM ratings (movie_id INT KEY, rating DOUBLE)
  WITH (KAFKA_TOPIC='ratings', 
        PARTITIONS=1, 
        VALUE_FORMAT='JSON');
```

And this is the table definition containing the movie reference data:

```sql
CREATE TABLE movies (id INT PRIMARY KEY, title VARCHAR, release_year INT)
    WITH (KAFKA_TOPIC='movies', 
          PARTITIONS=1, 
          VALUE_FORMAT='JSON');
```

Note that for a stream-table join to succeed, the primary key of the table must be the key of the stream.
For example, the `movies` primary key `id` matches up with the `ratings` stream key of `movie_id`.

With your stream and table in place you can build a join like this:

```sql
CREATE STREAM rated_movies AS
    SELECT ratings.movie_id AS id, title, rating
    FROM ratings
    LEFT JOIN movies ON ratings.movie_id = movies.id;
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

  Run the following SQL statements to create the `ratings` stream and `movies` table backed by Kafka running in Docker and 
  populate them with test data.

  ```sql
  CREATE STREAM ratings (movie_id INT KEY, rating DOUBLE)
    WITH (KAFKA_TOPIC='ratings', 
          PARTITIONS=1, 
          VALUE_FORMAT='JSON');
  ```

  ```sql
  CREATE TABLE movies (id INT PRIMARY KEY, title VARCHAR, release_year INT)
      WITH (KAFKA_TOPIC='movies', 
            PARTITIONS=1, 
            VALUE_FORMAT='JSON');
  ```

  ```sql
  INSERT INTO movies (id, title, release_year) VALUES (294, 'Twisters', 2024);
  INSERT INTO movies (id, title, release_year) VALUES (354, 'Unfrosted', 2024);
  INSERT INTO movies (id, title, release_year) VALUES (782, 'Family Switch', 2023);

  INSERT INTO ratings (movie_id, rating) VALUES (294, 8.2);
  INSERT INTO ratings (movie_id, rating) VALUES (294, 8.5);
  INSERT INTO ratings (movie_id, rating) VALUES (354, 9.9);
  INSERT INTO ratings (movie_id, rating) VALUES (354, 9.7);
  INSERT INTO ratings (movie_id, rating) VALUES (782, 7.8);
  INSERT INTO ratings (movie_id, rating) VALUES (782, 7.7);
  INSERT INTO ratings (movie_id, rating) VALUES (782, 2.1);
  ```

  Finally, run the stream-table join query and land the results in a new `rated_movies` stream. Note that we first
  tell ksqlDB to consume from the beginning of the streams.
  
  ```sql
  SET 'auto.offset.reset'='earliest';

  CREATE STREAM rated_movies AS
      SELECT ratings.movie_id AS id, title, rating
      FROM ratings
      LEFT JOIN movies ON ratings.movie_id = movies.id
      EMIT CHANGES;
  ```

  Query the new stream:

  ```sql
  SELECT *
  FROM rated_movies
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +----------------------+----------------------+----------------------+
  |ID                    |TITLE                 |RATING                |
  +----------------------+----------------------+----------------------+
  |294                   |Twisters              |8.2                   |
  |294                   |Twisters              |8.5                   |
  |354                   |Unfrosted             |9.9                   |
  |354                   |Unfrosted             |9.7                   |
  |782                   |Family Switch         |7.8                   |
  |782                   |Family Switch         |7.7                   |
  |782                   |Family Switch         |2.1                   |
  +----------------------+----------------------+----------------------+
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
  will consume from the beginning of the streams we create.

  Enter the following statements in the editor and click `Run query`. This creates the `ratings` stream and `movies` table
  and populates them with test data.

  ```sql
  CREATE STREAM ratings (movie_id INT KEY, rating DOUBLE)
    WITH (KAFKA_TOPIC='ratings', 
          PARTITIONS=1, 
          VALUE_FORMAT='JSON');

  CREATE TABLE movies (id INT PRIMARY KEY, title VARCHAR, release_year INT)
      WITH (KAFKA_TOPIC='movies', 
            PARTITIONS=1, 
            VALUE_FORMAT='JSON');

  INSERT INTO movies (id, title, release_year) VALUES (294, 'Twisters', 2024);
  INSERT INTO movies (id, title, release_year) VALUES (354, 'Unfrosted', 2024);
  INSERT INTO movies (id, title, release_year) VALUES (782, 'Family Switch', 2023);

  INSERT INTO ratings (movie_id, rating) VALUES (294, 8.2);
  INSERT INTO ratings (movie_id, rating) VALUES (294, 8.5);
  INSERT INTO ratings (movie_id, rating) VALUES (354, 9.9);
  INSERT INTO ratings (movie_id, rating) VALUES (354, 9.7);
  INSERT INTO ratings (movie_id, rating) VALUES (782, 7.8);
  INSERT INTO ratings (movie_id, rating) VALUES (782, 7.7);
  INSERT INTO ratings (movie_id, rating) VALUES (782, 2.1);
  ```

  Now, paste the stream-table join query in the editor and click `Run query`. This will land the results in a 
  new `rated_movies` stream.

  ```sql
  CREATE STREAM rated_movies AS
      SELECT ratings.movie_id AS id, title, rating
      FROM ratings
      LEFT JOIN movies ON ratings.movie_id = movies.id
      EMIT CHANGES;
  ```

  Query the new stream:

  ```sql
  SELECT *
  FROM rated_movies
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +----------------------+----------------------+----------------------+
  |ID                    |TITLE                 |RATING                |
  +----------------------+----------------------+----------------------+
  |294                   |Twisters              |8.2                   |
  |294                   |Twisters              |8.5                   |
  |354                   |Unfrosted             |9.9                   |
  |354                   |Unfrosted             |9.7                   |
  |782                   |Family Switch         |7.8                   |
  |782                   |Family Switch         |7.7                   |
  |782                   |Family Switch         |2.1                   |
  +----------------------+----------------------+----------------------+
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
