<!-- title: How to join two tables in ksqlDB -->
<!-- description: In this tutorial, learn how to join two tables in ksqlDB, with step-by-step instructions and supporting code. -->

# How to join two tables in ksqlDB

Consider that you have two tables of reference data in Kafka topics, and you want to join them on a common key.

## Setup

For this example, let's say you have data about movies in one table, and you want to add additional information like who was the lead actor.

First, here's your table containing movie information:

```sql
CREATE TABLE movies (
        title VARCHAR PRIMARY KEY,
        id INT,
        release_year INT
   ) WITH (
       KAFKA_TOPIC='movies',
       PARTITIONS=1,
       VALUE_FORMAT='JSON'
   );
```

And here's a table containing additional movie data with information on actors:

```sql
CREATE TABLE lead_actors (
        title VARCHAR PRIMARY KEY,
        actor_name VARCHAR
    ) WITH (
         KAFKA_TOPIC='lead_actors',
         PARTITIONS=1,
         VALUE_FORMAT='JSON'
    );
```

For the join between these tables, you create another table containing your desired information:

```sql
CREATE TABLE movies_enriched AS
    SELECT m.id, m.title, m.release_year, l.actor_name
    FROM movies m
    INNER JOIN lead_actors l
    ON m.title = l.title;
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

  Run the following SQL statements to create the `movies` and `lead_actors` tables backed by Kafka running in Docker and 
  populate them with test data.

  ```sql
  CREATE TABLE movies (
          title VARCHAR PRIMARY KEY,
          id INT,
          release_year INT
     ) WITH (
         KAFKA_TOPIC='movies',
         PARTITIONS=1,
         VALUE_FORMAT='JSON'
     );
  ```

  ```sql
  CREATE TABLE lead_actors (
          title VARCHAR PRIMARY KEY,
          actor_name VARCHAR
      ) WITH (
           KAFKA_TOPIC='lead_actors',
           PARTITIONS=1,
           VALUE_FORMAT='JSON'
      );
  ```

  ```sql
  INSERT INTO lead_actors (TITLE, ACTOR_NAME) VALUES ('Twisters', 'Glen Powell');
  INSERT INTO lead_actors (TITLE, ACTOR_NAME) VALUES ('Barbie', 'Ryan Gosling');
  INSERT INTO lead_actors (TITLE, ACTOR_NAME) VALUES ('Oppenheimer', 'Cillian Murphy');
  INSERT INTO lead_actors (TITLE, ACTOR_NAME) VALUES ('Blink Twice', 'Channing Tatum');

  INSERT INTO movies (ID, TITLE, RELEASE_YEAR) VALUES (48, 'Twisters', 2024);
  INSERT INTO movies (ID, TITLE, RELEASE_YEAR) VALUES (294, 'Barbie', 2023);
  INSERT INTO movies (ID, TITLE, RELEASE_YEAR) VALUES (128, 'Oppenheimer', 2024);
  INSERT INTO movies (ID, TITLE, RELEASE_YEAR) VALUES (42, 'Blink Twice', 2024);
  ```

  Finally, run the table-table join query and land the results in a new `movies_enriched` table.
  
  ```sql
  CREATE TABLE movies_enriched AS
      SELECT m.id, m.title, m.release_year, l.actor_name
      FROM movies m
      INNER JOIN lead_actors l
      ON m.title = l.title;
  ```

  Query the new table:

  ```sql
  SELECT * FROM movies_enriched;
  ```

  The query output should look like this:

  ```plaintext
  +-------------------+-------------------+-------------------+-------------------+
  |M_TITLE            |ID                 |RELEASE_YEAR       |ACTOR_NAME         |
  +-------------------+-------------------+-------------------+-------------------+
  |Barbie             |294                |2023               |Ryan Gosling       |
  |Blink Twice        |42                 |2024               |Channing Tatum     |
  |Oppenheimer        |128                |2024               |Cillian Murphy     |
  |Twisters           |48                 |2024               |Glen Powell        |
  +-------------------+-------------------+-------------------+-------------------+
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

  Enter the following statements in the editor and click `Run query`. This creates the `movies` and `lead_actors` tables
  and populates them with test data.

  ```sql
  CREATE TABLE movies (
          title VARCHAR PRIMARY KEY,
          id INT,
          release_year INT
     ) WITH (
         KAFKA_TOPIC='movies',
         PARTITIONS=1,
         VALUE_FORMAT='JSON'
     );

  CREATE TABLE lead_actors (
          title VARCHAR PRIMARY KEY,
          actor_name VARCHAR
      ) WITH (
           KAFKA_TOPIC='lead_actors',
           PARTITIONS=1,
           VALUE_FORMAT='JSON'
      );

  INSERT INTO lead_actors (TITLE, ACTOR_NAME) VALUES ('Twisters', 'Glen Powell');
  INSERT INTO lead_actors (TITLE, ACTOR_NAME) VALUES ('Barbie', 'Ryan Gosling');
  INSERT INTO lead_actors (TITLE, ACTOR_NAME) VALUES ('Oppenheimer', 'Cillian Murphy');
  INSERT INTO lead_actors (TITLE, ACTOR_NAME) VALUES ('Blink Twice', 'Channing Tatum');

  INSERT INTO movies (ID, TITLE, RELEASE_YEAR) VALUES (48, 'Twisters', 2024);
  INSERT INTO movies (ID, TITLE, RELEASE_YEAR) VALUES (294, 'Barbie', 2023);
  INSERT INTO movies (ID, TITLE, RELEASE_YEAR) VALUES (128, 'Oppenheimer', 2024);
  INSERT INTO movies (ID, TITLE, RELEASE_YEAR) VALUES (42, 'Blink Twice', 2024);
  ```

  Now, paste the table-table join query in the editor and click `Run query`. This will land the results in a 
  new `movies_enriched` table.
  
  ```sql
  CREATE TABLE movies_enriched AS
      SELECT m.id, m.title, m.release_year, l.actor_name
      FROM movies m
      INNER JOIN lead_actors l
      ON m.title = l.title;
  ```

  Query the new table:

  ```sql
  SELECT * FROM movies_enriched;
  ```

  The query output should look like this:

  ```plaintext
  +-------------------+-------------------+-------------------+-------------------+
  |M_TITLE            |ID                 |RELEASE_YEAR       |ACTOR_NAME         |
  +-------------------+-------------------+-------------------+-------------------+
  |Barbie             |294                |2023               |Ryan Gosling       |
  |Blink Twice        |42                 |2024               |Channing Tatum     |
  |Oppenheimer        |128                |2024               |Cillian Murphy     |
  |Twisters           |48                 |2024               |Glen Powell        |
  +-------------------+-------------------+-------------------+-------------------+
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
