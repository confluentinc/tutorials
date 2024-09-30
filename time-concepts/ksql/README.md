<!-- title: How to implement event-time semantics in ksqlDB -->
<!-- description: In this tutorial, learn how to implement event-time semantics in ksqlDB, with step-by-step instructions and supporting code. -->

# How to implement event-time semantics in ksqlDB

By default, time-based aggregations in ksqlDB (tumbling windows, hopping windows, etc.) operate on the timestamp in the record metadata, which could be either 'CreateTime' (the producer system time) or 'LogAppendTime' (the broker system time), depending on the `message.timestamp.type` topic configuration value. 'CreateTime' may help with event-time semantics, but in some use cases, the desired event time is a timestamp embedded inside the record payload itself.

For example, consider a topic of temperature sensor readings that contains the temperature and timestamp of the reading. 
In order to achieve event-time semantics, we specify a field in the record payload as the `TIMESTAMP` attribute when defining the stream:

```sql
CREATE STREAM temperature_event_time (temp DOUBLE, event_time BIGINT)
  WITH (KAFKA_TOPIC='temperature-event-time',
        PARTITIONS=1,
        VALUE_FORMAT='AVRO',
        TIMESTAMP='event_time');
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

  Run the following SQL statements to create two streams: one that uses log-time semantics, and one that uses
  event-time semantics:

  ```sql
  CREATE STREAM temperature_log_time (temp DOUBLE, event_time BIGINT)
  WITH (KAFKA_TOPIC='temperature-log-time',
        PARTITIONS=1,
        VALUE_FORMAT='AVRO');
  ```

  ```sql
  CREATE STREAM temperature_event_time (temp DOUBLE, event_time BIGINT)
      WITH (KAFKA_TOPIC='temperature-event-time',
            PARTITIONS=1,
            VALUE_FORMAT='AVRO',
            TIMESTAMP='event_time');
   ```

  Next, insert the same event into each stream:

  ```sql
  INSERT INTO temperature_log_time   (temp, event_time) VALUES (100.98, 1673560175029);
  INSERT INTO temperature_event_time (temp, event_time) VALUES (100.98, 1673560175029);
  ```

  Now if you query the `temperature_log_time` stream and include the system column `ROWTIME` that gets used for time-based aggregations, you'll see that the `ROWTIME` is the current time and not the time in the event payload. Note that we first tell ksqlDB to consume from the beginning of the stream.

  ```sql
  SET 'auto.offset.reset'='earliest';

  SELECT *, ROWTIME
  FROM temperature_log_time
  EMIT CHANGES;
  ```

  The query output will show different timestamps, e.g.:

  ```plaintext
  +-----------------------------+-----------------------------+-----------------------------+
  |TEMP                         |EVENT_TIME                   |ROWTIME                      |
  +-----------------------------+-----------------------------+-----------------------------+
  |100.98                       |1673560175029                |1727450542650                |
  +-----------------------------+-----------------------------+-----------------------------+
  ```

  Now query the `temperature_event_time` stream and include the system column `ROWTIME`:

  ```sql
  SELECT *, ROWTIME
  FROM temperature_event_time
  EMIT CHANGES;
  ```  

  The system column `ROWTIME` matches the timestamp in the event:

  ```plaintext
  +-----------------------------+-----------------------------+-----------------------------+
  |TEMP                         |EVENT_TIME                   |ROWTIME                      |
  +-----------------------------+-----------------------------+-----------------------------+
  |100.98                       |1673560175029                |1673560175029                |
  +-----------------------------+-----------------------------+-----------------------------+
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

  Enter the following statements in the editor and click `Run query`. This creates two streams: one that uses log-time semantics, and one that uses
  event-time semantics:

  ```sql
  CREATE STREAM temperature_log_time (temp DOUBLE, event_time BIGINT)
  WITH (KAFKA_TOPIC='temperature-log-time',
        PARTITIONS=1,
        VALUE_FORMAT='AVRO');

  CREATE STREAM temperature_event_time (temp DOUBLE, event_time BIGINT)
      WITH (KAFKA_TOPIC='temperature-event-time',
            PARTITIONS=1,
            VALUE_FORMAT='AVRO',
            TIMESTAMP='event_time');
   ```

  Next, insert the same event into each stream:

  ```sql
  INSERT INTO temperature_log_time   (temp, event_time) VALUES (100.98, 1673560175029);
  INSERT INTO temperature_event_time (temp, event_time) VALUES (100.98, 1673560175029);
  ```

  Now if you query the `temperature_log_time` stream and include the system column `ROWTIME` that gets used for time-based aggregations, you'll see that the `ROWTIME` is the current time and not the time in the event payload.

  ```sql
  SELECT *, ROWTIME
  FROM temperature_log_time
  EMIT CHANGES;
  ```

  The query output will show different timestamps, e.g.:

  ```plaintext
  +-----------------------------+-----------------------------+-----------------------------+
  |TEMP                         |EVENT_TIME                   |ROWTIME                      |
  +-----------------------------+-----------------------------+-----------------------------+
  |100.98                       |1673560175029                |1727450542650                |
  +-----------------------------+-----------------------------+-----------------------------+
  ```

  Now query the `temperature_event_time` stream and include the system column `ROWTIME`:

  ```sql
  SELECT *, ROWTIME
  FROM temperature_event_time
  EMIT CHANGES;
  ```  

  The system column `ROWTIME` matches the timestamp in the event:

  ```plaintext
  +-----------------------------+-----------------------------+-----------------------------+
  |TEMP                         |EVENT_TIME                   |ROWTIME                      |
  +-----------------------------+-----------------------------+-----------------------------+
  |100.98                       |1673560175029                |1673560175029                |
  +-----------------------------+-----------------------------+-----------------------------+
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
