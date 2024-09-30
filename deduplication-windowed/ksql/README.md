<!-- title: How to filter duplicate events per time window from a Kafka topic with ksqlDB -->
<!-- description: In this tutorial, learn how to filter duplicate events per time window from a Kafka topic with ksqlDB, with step-by-step instructions and supporting code. -->

# How to filter duplicate events per time window from a Kafka topic with ksqlDB

Consider a topic with events that represent clicks on a website. Each event contains an IP address, a URL, and a timestamp.
In this tutorial, we'll use ksqlDB to deduplicate these click events.

## Setup

Let's start with a stream representing user click events:

```sql
CREATE STREAM clicks (ip_address VARCHAR, url VARCHAR)
    WITH (KAFKA_TOPIC='clicks',
          PARTITIONS=1,
          FORMAT='JSON');
```

## Deduplicate events per time window

The first step toward deduplication is to create a table to collect the click events by time window:

```sql
CREATE TABLE detected_clicks AS
    SELECT
        ip_address AS key1,
        url AS key2,
        AS_VALUE(ip_address) AS ip_address,
        COUNT(ip_address) as ip_count,
        AS_VALUE(url) AS url,
        FORMAT_timestamp(FROM_UNIXTIME(EARLIEST_BY_OFFSET(ROWTIME)), 'yyyy-MM-dd HH:mm:ss.SSS') AS timestamp
    FROM clicks WINDOW TUMBLING (SIZE 2 MINUTES, RETENTION 1000 DAYS)
    GROUP BY ip_address, url
    EMIT CHANGES;
```

As we’re grouping by IP address and URL, these columns will become part of the primary key of the table. Primary key columns are stored in the Kafka message’s key. As we’ll need them in the value later, we use `AS_VALUE` to copy the columns into the value and set their name. To avoid the value column names clashing with the key columns, we add aliases to rename the key columns.

As it stands, the key of the `detected_clicks` table contains the IP address and URL columns, and as the table is windowed, the window start and end timestamps.
Create another stream that will only contain `ip_address`, `ip_count`, `url`, and `timestamp` from the topic backing the `detected_clicks` table:

```sql
CREATE STREAM raw_values_clicks (ip_address STRING, ip_count BIGINT, url STRING, timestamp STRING)
    WITH (KAFKA_TOPIC='DETECTED_CLICKS',
          PARTITIONS=1,
          FORMAT='JSON');
```

Now we can filter out duplicates by only retrieving records with a `ip_count` of 1:

```sql
SELECT
    ip_address,
    url,
    timestamp
FROM raw_values_clicks
WHERE ip_count = 1
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

  Run the following SQL statements to create the `clicks` stream backed by Kafka running in Docker and populate it with
  test data.

  ```sql
  CREATE STREAM clicks (ip_address VARCHAR, url VARCHAR)
      WITH (KAFKA_TOPIC='clicks',
            PARTITIONS=1,
            FORMAT='JSON');
  ```

  ```sql
  INSERT INTO clicks (ip_address, url) VALUES ('10.0.0.1', 'https://docs.confluent.io/current/tutorials/examples/kubernetes/gke-base/docs/index.html');
  INSERT INTO clicks (ip_address, url) VALUES ('10.0.0.12', 'https://www.confluent.io/hub/confluentinc/kafka-connect-datagen');
  INSERT INTO clicks (ip_address, url) VALUES ('10.0.0.13', 'https://www.confluent.io/hub/confluentinc/kafka-connect-datagen');

  INSERT INTO clicks (ip_address, url) VALUES ('10.0.0.1', 'https://docs.confluent.io/current/tutorials/examples/kubernetes/gke-base/docs/index.html');
  INSERT INTO clicks (ip_address, url) VALUES ('10.0.0.12', 'https://www.confluent.io/hub/confluentinc/kafka-connect-datagen');
  INSERT INTO clicks (ip_address, url) VALUES ('10.0.0.13', 'https://www.confluent.io/hub/confluentinc/kafka-connect-datagen');
  ```

  Next, create a table to collect the click events by time window. Note that we first tell ksqlDB to consume from the beginning of the stream.

  ```sql
  SET 'auto.offset.reset'='earliest';

  CREATE TABLE detected_clicks AS
      SELECT
          ip_address AS key1,
          url AS key2,
          AS_VALUE(ip_address) AS ip_address,
          COUNT(ip_address) as ip_count,
          AS_VALUE(url) AS url,
          FORMAT_timestamp(FROM_UNIXTIME(EARLIEST_BY_OFFSET(ROWTIME)), 'yyyy-MM-dd HH:mm:ss.SSS') AS timestamp
      FROM clicks WINDOW TUMBLING (SIZE 2 MINUTES, RETENTION 1000 DAYS)
      GROUP BY ip_address, url
      EMIT CHANGES;
  ```

  Now create another stream that will only contain `ip_address`, `ip_count`, `url`, and `timestamp` from the topic backing the `detected_clicks` table:

  ```sql
  CREATE STREAM raw_values_clicks (ip_address STRING, ip_count BIGINT, url STRING, timestamp STRING)
      WITH (KAFKA_TOPIC='DETECTED_CLICKS',
            PARTITIONS=1,
            FORMAT='JSON');
  ```

  Finally, filter out duplicates by only retrieving records with a `ip_count` of 1:

  ```sql
  SELECT
      ip_address,
      url,
      timestamp
  FROM raw_values_clicks
  WHERE ip_count = 1
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +-----------+----------------------------------------------------------------------------------------+-------------------------+
  |IP_ADDRESS |URL                                                                                     |TIMESTAMP                |
  +-----------+----------------------------------------------------------------------------------------+-------------------------+
  |10.0.0.1   |https://docs.confluent.io/current/tutorials/examples/kubernetes/gke-base/docs/index.html|2024-09-24 17:39:48.650  |
  |10.0.0.12  |https://www.confluent.io/hub/confluentinc/kafka-connect-datagen                         |2024-09-24 17:39:48.688  |
  |10.0.0.13  |https://www.confluent.io/hub/confluentinc/kafka-connect-datagen                         |2024-09-24 17:39:48.724  |
  +-----------+----------------------------------------------------------------------------------------+-------------------------+
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

  Enter the following statements in the editor and click `Run query`. This creates the `clicks` stream and
  populates it with test data.

  ```sql
  CREATE STREAM clicks (ip_address VARCHAR, url VARCHAR)
      WITH (KAFKA_TOPIC='clicks',
            PARTITIONS=1,
            FORMAT='JSON');

  INSERT INTO clicks (ip_address, url) VALUES ('10.0.0.1', 'https://docs.confluent.io/current/tutorials/examples/kubernetes/gke-base/docs/index.html');
  INSERT INTO clicks (ip_address, url) VALUES ('10.0.0.12', 'https://www.confluent.io/hub/confluentinc/kafka-connect-datagen');
  INSERT INTO clicks (ip_address, url) VALUES ('10.0.0.13', 'https://www.confluent.io/hub/confluentinc/kafka-connect-datagen');

  INSERT INTO clicks (ip_address, url) VALUES ('10.0.0.1', 'https://docs.confluent.io/current/tutorials/examples/kubernetes/gke-base/docs/index.html');
  INSERT INTO clicks (ip_address, url) VALUES ('10.0.0.12', 'https://www.confluent.io/hub/confluentinc/kafka-connect-datagen');
  INSERT INTO clicks (ip_address, url) VALUES ('10.0.0.13', 'https://www.confluent.io/hub/confluentinc/kafka-connect-datagen');
  ```

  Next, create a table to collect the click events by time window.

  ```sql
  CREATE TABLE detected_clicks AS
      SELECT
          ip_address AS key1,
          url AS key2,
          AS_VALUE(ip_address) AS ip_address,
          COUNT(ip_address) as ip_count,
          AS_VALUE(url) AS url,
          FORMAT_timestamp(FROM_UNIXTIME(EARLIEST_BY_OFFSET(ROWTIME)), 'yyyy-MM-dd HH:mm:ss.SSS') AS timestamp
      FROM clicks WINDOW TUMBLING (SIZE 2 MINUTES, RETENTION 1000 DAYS)
      GROUP BY ip_address, url
      EMIT CHANGES;
  ```

  Now create another stream that will only contain `ip_address`, `ip_count`, `url`, and `timestamp` from the topic backing the `detected_clicks` table:

  ```sql
  CREATE STREAM raw_values_clicks (ip_address STRING, ip_count BIGINT, url STRING, timestamp STRING)
      WITH (KAFKA_TOPIC='DETECTED_CLICKS',
            PARTITIONS=1,
            FORMAT='JSON');
  ```

  Finally, filter out duplicates by only retrieving records with a `ip_count` of 1:

  ```sql
  SELECT
      ip_address,
      url,
      timestamp
  FROM raw_values_clicks
  WHERE ip_count = 1
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +-----------+----------------------------------------------------------------------------------------+-------------------------+
  |IP_ADDRESS |URL                                                                                     |TIMESTAMP                |
  +-----------+----------------------------------------------------------------------------------------+-------------------------+
  |10.0.0.1   |https://docs.confluent.io/current/tutorials/examples/kubernetes/gke-base/docs/index.html|2024-09-24 17:39:48.650  |
  |10.0.0.12  |https://www.confluent.io/hub/confluentinc/kafka-connect-datagen                         |2024-09-24 17:39:48.688  |
  |10.0.0.13  |https://www.confluent.io/hub/confluentinc/kafka-connect-datagen                         |2024-09-24 17:39:48.724  |
  +-----------+----------------------------------------------------------------------------------------+-------------------------+
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
