<!-- title: How to work with maps with Flink SQL -->
<!-- description: In this tutorial, learn how to work with maps with Flink SQL, with step-by-step instructions and supporting code. -->

# How to work with maps with Flink SQL

Maps (a.k.a. associative arrays or dictionaries) are a widely used data structure across programming languages, and Flink SQL provides full-featured native support. In this tutorial, we will walk through how to define and populate map columns, provide examples to convert between maps and strings, and show how to aggregate by map value.

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

  Finally, run following SQL statement to create the `user_preferences` table.

  ```sql
  CREATE TABLE user_preferences (
      user_id INT,
      preferences MAP<STRING, STRING>
  ) WITH (
      'connector' = 'kafka',
      'topic' = 'user_preferences',
      'properties.bootstrap.servers' = 'broker:9092',
      'scan.startup.mode' = 'earliest-offset',
      'key.format' = 'raw',
      'key.fields' = 'user_id',
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

  Finally, run following SQL statement to create the `user_preferences` table.

  ```sql
  CREATE TABLE user_preferences (
      user_id INT,
      preferences MAP<STRING, STRING>
  );
  ```

</details>

## Insert into a map

Given the `user_preferences` table defined in the previous section, let's first insert a couple of records. Note that, in order to insert into a `MAP` column, use the `MAP[key1, value1, key2, value2, ...]`.

```sql
INSERT INTO user_preferences VALUES
    (0, MAP['lang', 'en-US', 'ui-mode', 'dark']),
    (1, MAP['lang', 'es-ES', 'ui-mode', 'light']),
    (2, MAP['lang', 'en-US', 'ui-mode', 'light']),
    (3, MAP['ui-mode', 'light']);
```

## Query a map

Let's query the `user_preferences` table:

```sql
SELECT * FROM user_preferences;
```

You will see that the `preferences` map is rendered as `key=value` pairs in curly braces:

```plaintext
user_id                    preferences
      0     {lang=en-US, ui-mode=dark}
      1    {lang=es-ES, ui-mode=light}
      2    {lang=en-US, ui-mode=light}
      3                {ui-mode=light}
```

We can use bracket notation to select specific values. For example, to select the user's language preference:

```sql
SELECT
    user_id,
    preferences['lang'] AS lang
FROM user_preferences;
```

If the specified key doesn't exist in the `MAP` column, then `NULL` is returned, which can be useful for filtering out or detecting incomplete records:

```sql
SELECT
    user_id
FROM user_preferences
WHERE preferences['lang'] IS NULL;
```

### Convert between strings and maps

The [`STR_TO_MAP`](https://docs.confluent.io/cloud/current/flink/reference/functions/string-functions.html#flink-sql-str-to-map-function) function takes a single string of key-value pairs along with optional delimiter strings and creates a `MAP` from it. For example:

```sql
INSERT INTO user_preferences VALUES
    (4, STR_TO_MAP('lang=en-GB,ui-mode=dark'));
```

To convert a map into a string, simply concatenate using the [`||` operator](https://docs.confluent.io/cloud/current/flink/reference/functions/string-functions.html#string1-string2), e.g.:

```sql
SELECT
    user_id,
    'language: ' || preferences['lang'] || ', UI Mode: ' || preferences['ui-mode'] AS prefs
FROM user_preferences;
```

### Aggregate by `MAP` values

Flink SQL supports aggregation by `MAP` values. Simply drill into the `MAP` in the `GROUP BY` expression:

```sql
SELECT
    preferences['ui-mode'] AS ui_mode,
    COUNT(*) AS ui_mode_count
FROM user_preferences
GROUP BY preferences['ui-mode'];
```

## Clean up

When you are finished, clean up the infrastructure used for this tutorial, either by deleting the environment that you created in Confluent Cloud, or, if running in Docker:

```shell
docker compose -f ./docker/docker-compose-flinksql.yml down
```
