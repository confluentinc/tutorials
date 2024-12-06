<!-- title: How to handle timestamps with Flink SQL -->
<!-- description: In this tutorial, learn how to handle timestamps with Flink SQL, with step-by-step instructions and supporting code. -->

# How to handle timestamps with Flink SQL

"This timestamp is a Unix epoch in seconds and that one is in milliseconds, and this other timestamp is an ISO 8601-formatted string. I need to convert them into a canonical format." Every developer has faced timestamp data type wrangling tasks like this regardless of the programming language they're using. Timestamp wrangling is a fact of the developer's life because timestamps come in different shapes and sizes. In this tutorial, we will demonstrate the various ways to deal with timestamps in Flink SQL, including what to do when defining watermarks for event time processing.

## Provision Kafka and Flink

You can run through this tutorial locally with the Flink SQL Client against Flink and Kafka running in Docker, or with Confluent Cloud. Run through these steps to provision Kafka and Flink.

<details>
  <summary>Local with Docker</summary>

  ### Prerequisites

  * Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
  * [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.

  ### Provision Kafka and Flink

  Clone the `confluentinc/tutorials` GitHub repository (if you haven't already) and navigate to the `tutorials` directory:

  ```shell
  git clone git@github.com:confluentinc/tutorials.git
  cd tutorials
  ```

  Start Flink and Kafka:

  ```shell
  docker compose -f ./docker/docker-compose-flinksql.yml up -d
  ```

  Next, open the Flink SQL Client CLI:

  ```shell
  docker exec -it flink-sql-client sql-client.sh
  ```

  Finally, run following SQL statement to create the `temperature_readings` table backed by Kafka running in Docker. Observe that we begin our timestamp transformation journey with timestamps in a common format: the Unix epoch, i.e., the number of seconds that have elapsed since January 1, 1970, at midnight UTC. We define it as a `BIGINT`.

  ```sql
  CREATE TABLE temperature_readings (
      sensor_id INT,
      temperature DOUBLE,
      event_ts_epoch_seconds BIGINT
  ) WITH (
      'connector' = 'kafka',
      'topic' = 'temperature-readings',
      'properties.bootstrap.servers' = 'broker:9092',
      'scan.startup.mode' = 'earliest-offset',
      'key.format' = 'raw',
      'key.fields' = 'sensor_id',
      'value.format' = 'avro-confluent',
      'value.avro-confluent.url' = 'http://schema-registry:8081',
      'value.fields-include' = 'EXCEPT_KEY'
  );
  ```
</details>

<details>
  <summary>Confluent Cloud</summary>

  ### Prerequisites

  * A [Confluent Cloud](https://confluent.cloud/signup) account
  * A Flink compute pool created in Confluent Cloud. Follow [this](https://docs.confluent.io/cloud/current/flink/get-started/quick-start-cloud-console.html) quick start to create one. 

  Note: create all resources in a new Confluent Cloud environment so that clean up is easier

  ### Provision Kafka and Flink

  In the Confluent Cloud Console, navigate to your environment and then click the `Open SQL Workspace` button for the compute pool that you have created. Create a new 

  Select the default catalog (Confluent Cloud environment) and database (Kafka cluster) to use with the dropdowns at the top right.

  Finally, run following SQL statement to create the `temperature_readings` table. Observe that we begin our timestamp transformation journey with timestamps in a common format: the Unix epoch, i.e., the number of seconds that have elapsed since January 1, 1970, at midnight UTC. We define it as a `BIGINT`.

  ```sql
  CREATE TABLE temperature_readings (
      sensor_id INT,
      temperature DOUBLE,
      event_ts_epoch_seconds BIGINT
  );
  ```

</details>

## Timestamp usage and transformation

Given the `temperature_readings` table defined in the previous section, let's first insert a record that we will use to demonstrate the various timestamp wrangling tasks that follow. The timestamp of the record is February 13, 2009, at 11:31:30 PM UTC:

```sql
INSERT INTO temperature_readings VALUES
  (0, 100.32, UNIX_TIMESTAMP('2009-02-13 23:31:30.000 +0000', 'yyyy-MM-dd HH:mm:ss.SSS Z'));
```

Select the timestamp from this record:

```sql
SELECT event_ts_epoch_seconds FROM temperature_readings;
```

You will see that our timestamp is a particularly visually appealing one celebrated by computing trivia enthusiasts worldwide: `1234567890`. (We could have inserted this numeric directly but would have lost the exciting reveal.)

### Use epoch timestamp for event time processing

If you have an epoch timestamp that you'd like to use in defining [watermarks](https://developer.confluent.io/courses/flink-sql/watermarks/), you won't be able to use it directly. Try to use it:

_Important: Confluent Cloud provides a watermark by default, so use `MODIFY WATERMARK` rather than `ADD WATERMARK` if you are running this tutorial in Confluent Cloud._

```sql
ALTER TABLE temperature_readings
ADD WATERMARK FOR event_ts_epoch_seconds AS event_ts_epoch_seconds;
```

You will hit an error:

```plaintext
Invalid data type of time field for watermark definition. The field must be of type TIMESTAMP(p) or TIMESTAMP_LTZ(p), the supported precision 'p' is from 0 to 3, but the time field type is BIGINT
```

To get around this, you can add a computed column of the proper data type using the [`TO_TIMESTAMP_LTZ`](https://docs.confluent.io/cloud/current/flink/reference/functions/datetime-functions.html#flink-sql-to-timestamp-ltz-function) function. The following DDL statement will add a new computed timestamp column of type `TIMESTAMP_LTZ(0)`:

```sql
ALTER TABLE temperature_readings
ADD event_ts_ltz AS TO_TIMESTAMP_LTZ(event_ts_epoch_seconds, 0);
```

_Important: the precision of `0` is important and must agree with the precision of the epoch timestamp. If we had instead specified a precision of `3` with  `TO_TIMESTAMP_LTZ(event_ts_epoch_seconds, 3)`, we would get an incorrect timestamp in 1970._

Query the table:

```sql
SELECT * FROM temperature_readings;
```

And you will see the new computed column:

```sql
   sensor_id  temperature  event_ts_epoch_seconds             event_ts_ltz
           0       100.32              1234567890  2009-02-13 23:31:30.000
```

The new column can now be used to define a watermark strategy for event time processing (remember to `MODIFY` rather than `ADD` if running in Confluent Cloud):

```sql
ALTER TABLE temperature_readings
ADD WATERMARK FOR event_ts_ltz AS event_ts_ltz;
```

### Convert epoch to native `TIMESTAMP`

The previous section showed how to create a `TIMESTAMP_LTZ` from a Unix epoch. In order to create a `TIMESTAMP` without local time zone, use the [`TO_TIMESTAMP`](https://docs.confluent.io/cloud/current/flink/reference/functions/datetime-functions.html#to-timestamp) function. Note that this function takes a string, so we first call [`FROM_UNIXTIME`](https://docs.confluent.io/cloud/current/flink/reference/functions/datetime-functions.html#flink-sql-from-unixtime-function) to convert the epoch to a string.

```sql
SELECT TO_TIMESTAMP(FROM_UNIXTIME(event_ts_epoch_seconds))
FROM temperature_readings;
```

### Convert epoch to string

The previous section introduced [`FROM_UNIXTIME`](https://docs.confluent.io/cloud/current/flink/reference/functions/datetime-functions.html#flink-sql-from-unixtime-function) to convert an epoch timestamp to a string. Note that it takes an optional second argument that specifies the format (a Java [`SimpleDateFormat`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/SimpleDateFormat.html) string):

```sql
SELECT
    FROM_UNIXTIME(event_ts_epoch_seconds),
    FROM_UNIXTIME(event_ts_epoch_seconds, 'yyyy-MM-dd hh:mm'),
    FROM_UNIXTIME(event_ts_epoch_seconds, 'yyyy-MM-dd HH:mm:ss.SSS Z')
FROM temperature_readings;
```

### Convert string to epoch

To convert a string to the corresponding epoch, use the [`UNIX_TIMESTAMP`](https://docs.confluent.io/cloud/current/flink/reference/functions/datetime-functions.html#flink-sql-unix-timestamp-2-function) function. Just as `FROM_UNIXTIME` takes an optional date format string to override the default of `yyyy-mm-dd hh:mm:ss`, `UNIX_TIMESTAMP` takes an optional date format string to specify the format. This is necessary to avoid any ambiguity. (Is `2024-01-07` January 7th or July 1st?)

For example:

```sql
SELECT
    UNIX_TIMESTAMP('2009-02-13 23:31:30.000 +0000', 'yyyy-MM-dd HH:mm:ss.SSS Z'),
    UNIX_TIMESTAMP('2009-02-13 11:31:30 PM UTC', 'yyyy-MM-dd hh:mm:ss a z'),
    UNIX_TIMESTAMP('Feb 13, 2009 23:31:30 PM UTC', 'MMM d, yyyy HH:mm:ss a z');
```

These all return the same epoch timestamp `1234567890`.

### Extract timestamp parts

Flink SQL has a few functions that can extract parts from a timestamp or date. Some of the functions like [`HOUR`](https://docs.confluent.io/cloud/current/flink/reference/functions/datetime-functions.html#hour), [`MINUTE`](https://docs.confluent.io/cloud/current/flink/reference/functions/datetime-functions.html#minute), and [`SECOND`](https://docs.confluent.io/cloud/current/flink/reference/functions/datetime-functions.html#second) take a timestamp, while others like [`DAYOFWEEK`](https://docs.confluent.io/cloud/current/flink/reference/functions/datetime-functions.html#dayofweek) (which returns a number 1-7 where 1 is Sunday) take a `DATE` and thus would need to be cast from a timestamp to a date first. We have seen in this tutorial how [`TO_TIMESTAMP_LTZ`](https://docs.confluent.io/cloud/current/flink/reference/functions/datetime-functions.html#to-timestamp-ltz) and [`TO_TIMESTAMP`](https://docs.confluent.io/cloud/current/flink/reference/functions/datetime-functions.html#to-timestamp) can be used to convert an epoch timestamp to a `TIMESTAMP_LTZ` or `TIMESTAMP`. [`TO_DATE`](https://docs.confluent.io/cloud/current/flink/reference/functions/datetime-functions.html#to-date) can similarly be used to convert to a date, though it operates on a string so `TO_DATE(FROM_UNIXTIME(epoch_timestamp_col))` will do the conversion. In the following example, we do just that in order to extract the day of the week that our timestamp fell on. Note that we have an outer query that converts the numeric day of week to a human-readable form.

```sql
SELECT
    event_ts_epoch_seconds,
    CASE
        WHEN dow = 1 then 'Sun'
        WHEN dow = 2 then 'Mon'
        WHEN dow = 3 then 'Tue'
        WHEN dow = 4 then 'Wed'
        WHEN dow = 5 then 'Thu'
        WHEN dow = 6 then 'Fri'
        WHEN dow = 7 then 'Sat'
    END AS day_of_week
FROM (
         SELECT
             event_ts_epoch_seconds,
             DAYOFWEEK(TO_DATE(FROM_UNIXTIME(event_ts_epoch_seconds))) as dow
         FROM temperature_readings
     );
```

This shows us that the epoch timestamp `1234567890` fell on a Friday. Beware: the time zone matters! In Shanghai, which is 8 hours _ahead_ of UTC, this timestamp fell on a Saturday, which we can see if we set the local time zone and rerun the query:

```sql
SET 'table.local-time-zone' = 'Asia/Shanghai';
```

### Convert time zone

Converting a timestamp from one time zone to another is straightforward with the [`CONVERT_TZ`](https://docs.confluent.io/cloud/current/flink/reference/functions/datetime-functions.html#flink-sql-convert-tz-function) function. This function takes a string datetime (default format `yyyy-MM-dd hh:mm:ss`) as well as the source and destination timezone. Building on the time zone caution from the previous section, we can see that Friday February 13th at 11:31:30 UTC is indeed 8 hours ahead in Shanghai:

```sql
SELECT CONVERT_TZ('2009-02-13 23:31:30', 'UTC', 'Asia/Shanghai');
```

This query outputs `2009-02-14 07:31:30`.

## Clean up

When you are finished, clean up the infrastructure used for this tutorial, either by deleting the environment that you created in Confluent Cloud, or, if running in Docker:

```shell
docker compose -f ./docker/docker-compose-flinksql.yml down
```
