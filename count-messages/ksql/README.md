<!-- title: How to count the number of messages in a topic with ksqlDB -->
<!-- description: In this tutorial, learn how to count the number of messages in a topic with ksqlDB, with step-by-step instructions and supporting code. -->

# How to count the number of messages in a topic with ksqlDB

It can be useful to know how many messages are currently in a topic, but you cannot calculate this directly based on the offsets, because you need to consider the topic's retention policy, log compaction, and potential duplicate messages. In this example, we'll take a topic of pageview data and see how we can count all the messages in the topic. Note that the time complexity for this tutorial is O(n) (linear); processing time will depend on the number of messages in the topic, and large data sets will require long running times.

## Setup

First, create a stream over the topic you're interested in counting the number of records.

```sql
CREATE STREAM pageviews (msg VARCHAR)
  WITH (KAFKA_TOPIC ='pageviews',
        VALUE_FORMAT='JSON');
```

Note that at this stage we’re just interested in counting the messages in their entirety, so we define the loosest schema possible, `msg VARCHAR`, for speed.
Also, you'll need to configure ksqlDB to start from the beginning of the topic so that all messages are included in the count:

```text
SET 'auto.offset.reset' = 'earliest';
```

Then `COUNT` the events in the `pageviews` stream:

```sql
SELECT COUNT(*) AS msg_count
FROM pageviews
EMIT CHANGES;
```

The query above will run continually until you cancel it.

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

  Run the following SQL statements to create the `pageviews` stream backed by Kafka running in Docker and populate it with
  test data.

  ```sql
  CREATE STREAM pageviews (msg VARCHAR)
    WITH (KAFKA_TOPIC ='pageviews',
          PARTITIONS=1,
          VALUE_FORMAT='JSON');
  ```

  ```sql
  INSERT INTO pageviews (msg) VALUES ('https://www.acme.com/home');
  INSERT INTO pageviews (msg) VALUES ('https://www.acme.com/search');
  INSERT INTO pageviews (msg) VALUES ('https://www.acme.com/home');
  INSERT INTO pageviews (msg) VALUES ('https://www.acme.com/products');
  ```

  Finally, run the aggregating count query. Note that we first tell ksqlDB to consume from the beginning of the stream,
  and we also configure the query to use caching so that we only get a single count result.

  ```sql
  SET 'auto.offset.reset'='earliest';
  SET 'ksql.streams.cache.max.bytes.buffering' = '10000000';

  SELECT COUNT(*) AS msg_count
  FROM pageviews
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +----------------------------------------+
  |MSG_COUNT                               |
  +----------------------------------------+
  |4                                       |
  +----------------------------------------+
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
  will consume from the beginning of the stream we create. Then click `Add another field` and add a property
  `cache.max.bytes.buffering` with value `10000000`. This configures the count query to use caching so that we only get
  a single final count in this example as oppposed to updates for every event in the `pageviews` stream.

  Enter the following statements in the editor and click `Run query`. This creates the `pageviews` stream and
  populates it with test data.

  ```sql
  CREATE STREAM pageviews (msg VARCHAR)
    WITH (KAFKA_TOPIC ='pageviews',
          PARTITIONS=1,
          VALUE_FORMAT='JSON');

  INSERT INTO pageviews (msg) VALUES ('https://www.acme.com/home');
  INSERT INTO pageviews (msg) VALUES ('https://www.acme.com/search');
  INSERT INTO pageviews (msg) VALUES ('https://www.acme.com/home');
  INSERT INTO pageviews (msg) VALUES ('https://www.acme.com/products');
  ```

  Now paste the count query in the editor and click `Run query`:

  ```sql
  SELECT COUNT(*) AS msg_count
  FROM pageviews
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +----------------------------------------+
  |MSG_COUNT                               |
  +----------------------------------------+
  |4                                       |
  +----------------------------------------+
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
