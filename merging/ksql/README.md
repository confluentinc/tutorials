<!-- title: How to merge multiple streams with ksqlDB -->
<!-- description: In this tutorial, learn how to merge multiple streams with ksqlDB, with step-by-step instructions and supporting code. -->

# How to merge multiple streams with ksqlDB

In this tutorial, we take a look at the case when you have multiple streams, but you'd like to merge them into one.  
We'll use multiple streams of a music catalog to demonstrate.

## Setup

Let's say you have a stream of songs in the rock genre:

```sql
CREATE STREAM rock_songs (artist VARCHAR, title VARCHAR)
    WITH (KAFKA_TOPIC='rock_songs', PARTITIONS=1, VALUE_FORMAT='AVRO');
```

And another one with classical music:

```sql
CREATE STREAM classical_songs (artist VARCHAR, title VARCHAR)
    WITH (KAFKA_TOPIC='classical_songs', PARTITIONS=1, VALUE_FORMAT='AVRO');
```

You want to combine them into a single stream where in addition to the `artist` and `title` columns you'll add a `genre`:
```sql
CREATE STREAM all_songs (artist VARCHAR, title VARCHAR, genre VARCHAR)
    WITH (KAFKA_TOPIC='all_songs', PARTITIONS=1, VALUE_FORMAT='AVRO');
```

To merge the two streams into the new one, you'll execute statements that will select everything from both streams and insert them into the `all_songs` stream and populate the `genre` column as well:
 
```sql
INSERT INTO all_songs SELECT artist, title, 'rock' AS genre FROM rock_songs;
INSERT INTO all_songs SELECT artist, title, 'classical' AS genre FROM classical_songs;
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

  Run the following SQL statements to create the `rock_songs`, `classical_songs`, and `all_songs` streams backed by Kafka running in Docker and 
  populate the first two with test data.

  ```sql
  CREATE STREAM rock_songs (artist VARCHAR, title VARCHAR)
      WITH (KAFKA_TOPIC='rock_songs', PARTITIONS=1, VALUE_FORMAT='AVRO');
  ```

  ```sql
  CREATE STREAM classical_songs (artist VARCHAR, title VARCHAR)
      WITH (KAFKA_TOPIC='classical_songs', PARTITIONS=1, VALUE_FORMAT='AVRO');
  ```

  ```sql
  INSERT INTO rock_songs (artist, title) VALUES ('Metallica', 'Fade to Black');
  INSERT INTO rock_songs (artist, title) VALUES ('Smashing Pumpkins', 'Today');
  INSERT INTO rock_songs (artist, title) VALUES ('Pink Floyd', 'Another Brick in the Wall');
  INSERT INTO rock_songs (artist, title) VALUES ('Van Halen', 'Jump');
  INSERT INTO rock_songs (artist, title) VALUES ('Led Zeppelin', 'Kashmir');

  INSERT INTO classical_songs (artist, title) VALUES ('Wolfgang Amadeus Mozart', 'The Magic Flute');
  INSERT INTO classical_songs (artist, title) VALUES ('Johann Pachelbel', 'Canon');
  INSERT INTO classical_songs (artist, title) VALUES ('Ludwig van Beethoven', 'Symphony No. 5');
  INSERT INTO classical_songs (artist, title) VALUES ('Edward Elgar', 'Pomp and Circumstance');
  ```

  ```sql
  CREATE STREAM all_songs (artist VARCHAR, title VARCHAR, genre VARCHAR)
      WITH (KAFKA_TOPIC='all_songs', PARTITIONS=1, VALUE_FORMAT='AVRO');
  ```

  Next, run the two `INSERT` statements that will select everything from both streams and insert them into the 
  `all_songs` stream while also populating the `genre` column. Note that we first
  tell ksqlDB to consume from the beginning of the streams.

  ```sql
  SET 'auto.offset.reset'='earliest';

  INSERT INTO all_songs SELECT artist, title, 'rock' AS genre FROM rock_songs;
  INSERT INTO all_songs SELECT artist, title, 'classical' AS genre FROM classical_songs;
  ```

  Query the new stream:

  ```sql
  SELECT * FROM all_songs;
  ```

  The query output should look like this:

  ```plaintext
  +-------------------------------+-------------------------------+-------------------------------+
  |ARTIST                         |TITLE                          |GENRE                          |
  +-------------------------------+-------------------------------+-------------------------------+
  |Metallica                      |Fade to Black                  |rock                           |
  |Smashing Pumpkins              |Today                          |rock                           |
  |Pink Floyd                     |Another Brick in the Wall      |rock                           |
  |Van Halen                      |Jump                           |rock                           |
  |Led Zeppelin                   |Kashmir                        |rock                           |
  |Wolfgang Amadeus Mozart        |The Magic Flute                |classical                      |
  |Johann Pachelbel               |Canon                          |classical                      |
  |Ludwig van Beethoven           |Symphony No. 5                 |classical                      |
  |Edward Elgar                   |Pomp and Circumstance          |classical                      |
  +-------------------------------+-------------------------------+-------------------------------+
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
  will consume from the beginning of the streams we create.

  Enter the following statements in the editor and click `Run query`. This creates the  `rock_songs`, `classical_songs`,
  and `all_songs` streams and populates the first two with test data.

  ```sql
  CREATE STREAM rock_songs (artist VARCHAR, title VARCHAR)
      WITH (KAFKA_TOPIC='rock_songs', PARTITIONS=1, VALUE_FORMAT='AVRO');

  CREATE STREAM classical_songs (artist VARCHAR, title VARCHAR)
      WITH (KAFKA_TOPIC='classical_songs', PARTITIONS=1, VALUE_FORMAT='AVRO');

  INSERT INTO rock_songs (artist, title) VALUES ('Metallica', 'Fade to Black');
  INSERT INTO rock_songs (artist, title) VALUES ('Smashing Pumpkins', 'Today');
  INSERT INTO rock_songs (artist, title) VALUES ('Pink Floyd', 'Another Brick in the Wall');
  INSERT INTO rock_songs (artist, title) VALUES ('Van Halen', 'Jump');
  INSERT INTO rock_songs (artist, title) VALUES ('Led Zeppelin', 'Kashmir');

  INSERT INTO classical_songs (artist, title) VALUES ('Wolfgang Amadeus Mozart', 'The Magic Flute');
  INSERT INTO classical_songs (artist, title) VALUES ('Johann Pachelbel', 'Canon');
  INSERT INTO classical_songs (artist, title) VALUES ('Ludwig van Beethoven', 'Symphony No. 5');
  INSERT INTO classical_songs (artist, title) VALUES ('Edward Elgar', 'Pomp and Circumstance');

  CREATE STREAM all_songs (artist VARCHAR, title VARCHAR, genre VARCHAR)
      WITH (KAFKA_TOPIC='all_songs', PARTITIONS=1, VALUE_FORMAT='AVRO');
  ```

  Now, paste each `INSERT` statement in the editor and click `Run query`.
  
  ```sql
  INSERT INTO all_songs SELECT artist, title, 'rock' AS genre FROM rock_songs;
  INSERT INTO all_songs SELECT artist, title, 'classical' AS genre FROM classical_songs;
  ```

  Query the new stream:

  ```sql
  SELECT * FROM all_songs;
  ```

  The query output should look like this:

  ```plaintext
  +-------------------------------+-------------------------------+-------------------------------+
  |ARTIST                         |TITLE                          |GENRE                          |
  +-------------------------------+-------------------------------+-------------------------------+
  |Metallica                      |Fade to Black                  |rock                           |
  |Smashing Pumpkins              |Today                          |rock                           |
  |Pink Floyd                     |Another Brick in the Wall      |rock                           |
  |Van Halen                      |Jump                           |rock                           |
  |Led Zeppelin                   |Kashmir                        |rock                           |
  |Wolfgang Amadeus Mozart        |The Magic Flute                |classical                      |
  |Johann Pachelbel               |Canon                          |classical                      |
  |Ludwig van Beethoven           |Symphony No. 5                 |classical                      |
  |Edward Elgar                   |Pomp and Circumstance          |classical                      |
  +-------------------------------+-------------------------------+-------------------------------+
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
