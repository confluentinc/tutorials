<!-- title: How to filter messages in a Kafka topic with ksqlDB -->
<!-- description: In this tutorial, learn how to filter messages in a Kafka topic with ksqlDB, with step-by-step instructions and supporting code. -->

# How to filter messages in a Kafka topic with ksqlDB

How do you filter messages in a Kafka topic to contain only those that you're interested in? In this tutorial, we will
filter a stream of book publications down to those by a particular author.

## Setup

First, let's create a base stream of events containing book publications:

```sql
CREATE STREAM all_publications (bookid BIGINT KEY, 
                                author VARCHAR, 
                                title VARCHAR)
    WITH (KAFKA_TOPIC='publication_events'
          PARTITIONS=1,
          VALUE_FORMAT='JSON');
```

To create a new topic containing only events for a particular author, we issue a `CREATE STREAM AS SELECT` query containing
the appropriate `WHERE` clause:

```sql
CREATE STREAM george_martin WITH (KAFKA_TOPIC='george_martin_books') AS
    SELECT *
      FROM all_publications
      WHERE author = 'George R. R. Martin';
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

  Run the following SQL statements to create the `all_publications` stream backed by Kafka running in Docker and populate it with
  test data.

  ```sql
  CREATE STREAM all_publications (book_id BIGINT KEY, 
                                  author VARCHAR, 
                                  title VARCHAR)
      WITH (KAFKA_TOPIC='publication_events',
            PARTITIONS=1,
            VALUE_FORMAT='JSON');
  ```

  ```sql
  INSERT INTO all_publications (book_id, author, title) VALUES (1, 'C.S. Lewis', 'The Silver Chair');
  INSERT INTO all_publications (book_id, author, title) VALUES (2, 'George R. R. Martin', 'A Song of Ice and Fire');
  INSERT INTO all_publications (book_id, author, title) VALUES (3, 'C.S. Lewis', 'Perelandra');
  INSERT INTO all_publications (book_id, author, title) VALUES (4, 'George R. R. Martin', 'Fire & Blood');
  INSERT INTO all_publications (book_id, author, title) VALUES (5, 'J. R. R. Tolkien', 'The Hobbit');
  INSERT INTO all_publications (book_id, author, title) VALUES (6, 'J. R. R. Tolkien', 'The Lord of the Rings');
  INSERT INTO all_publications (book_id, author, title) VALUES (7, 'George R. R. Martin', 'A Dream of Spring');
  INSERT INTO all_publications (book_id, author, title) VALUES (8, 'J. R. R. Tolkien', 'The Fellowship of the Ring');
  INSERT INTO all_publications (book_id, author, title) VALUES (9, 'George R. R. Martin', 'The Ice Dragon');
  ```

  Finally, run the filter query to find books by George R. R. Martin and write the events to a new topic. Note that we 
  first tell ksqlDB to consume from the beginning of the stream.

  ```sql
  SET 'auto.offset.reset'='earliest';

  CREATE STREAM george_martin WITH (KAFKA_TOPIC='george_martin_books') AS
    SELECT *
      FROM all_publications
      WHERE author = 'George R. R. Martin';
  ```

  Query the new topic:

  ```sql
  SELECT *
  FROM george_martin
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +-------------------------------+-------------------------------+-------------------------------+
  |BOOK_ID                        |AUTHOR                         |TITLE                          |
  +-------------------------------+-------------------------------+-------------------------------+
  |2                              |George R. R. Martin            |A Song of Ice and Fire         |
  |4                              |George R. R. Martin            |Fire & Blood                   |
  |7                              |George R. R. Martin            |A Dream of Spring              |
  |9                              |George R. R. Martin            |The Ice Dragon                 |
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
  will consume from the beginning of the stream we create.

  Enter the following statements in the editor and click `Run query`. This creates the `all_publications` stream and
  populates it with test data.

  ```sql
  CREATE STREAM all_publications (book_id BIGINT KEY, 
                                  author VARCHAR, 
                                  title VARCHAR)
      WITH (KAFKA_TOPIC='publication_events',
            PARTITIONS=1,
            VALUE_FORMAT='JSON');

  INSERT INTO all_publications (book_id, author, title) VALUES (1, 'C.S. Lewis', 'The Silver Chair');
  INSERT INTO all_publications (book_id, author, title) VALUES (2, 'George R. R. Martin', 'A Song of Ice and Fire');
  INSERT INTO all_publications (book_id, author, title) VALUES (3, 'C.S. Lewis', 'Perelandra');
  INSERT INTO all_publications (book_id, author, title) VALUES (4, 'George R. R. Martin', 'Fire & Blood');
  INSERT INTO all_publications (book_id, author, title) VALUES (5, 'J. R. R. Tolkien', 'The Hobbit');
  INSERT INTO all_publications (book_id, author, title) VALUES (6, 'J. R. R. Tolkien', 'The Lord of the Rings');
  INSERT INTO all_publications (book_id, author, title) VALUES (7, 'George R. R. Martin', 'A Dream of Spring');
  INSERT INTO all_publications (book_id, author, title) VALUES (8, 'J. R. R. Tolkien', 'The Fellowship of the Ring');
  INSERT INTO all_publications (book_id, author, title) VALUES (9, 'George R. R. Martin', 'The Ice Dragon');
  ```

  Now paste the filter query to find books by George R. R. Martin and write the events to a new topic and click `Run query`:

  ```sql
  CREATE STREAM george_martin WITH (KAFKA_TOPIC='george_martin_books') AS
    SELECT *
      FROM all_publications
      WHERE author = 'George R. R. Martin';
  ```

  Query the new topic:

  ```sql
  SELECT *
  FROM george_martin
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +-------------------------------+-------------------------------+-------------------------------+
  |BOOK_ID                        |AUTHOR                         |TITLE                          |
  +-------------------------------+-------------------------------+-------------------------------+
  |2                              |George R. R. Martin            |A Song of Ice and Fire         |
  |4                              |George R. R. Martin            |Fire & Blood                   |
  |7                              |George R. R. Martin            |A Dream of Spring              |
  |9                              |George R. R. Martin            |The Ice Dragon                 |
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
