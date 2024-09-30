<!-- title: How to calculate the geographic distance between two points with ksqlDB -->
<!-- description: In this tutorial, learn how to calculate the geographic distance between two points with ksqlDB, with step-by-step instructions and supporting code. -->

# How to calculate the geographic distance between two points with ksqlDB

You can use the ksqlDB [`GEO_DISTANCE`](https://docs.ksqldb.io/en/latest/developer-guide/ksqldb-reference/scalar-functions/#GEO_DISTANCE) function to calculate the distance between two latitude and longitude points.

## Setup

You have a topic that contains longitude and latitude data.  For our example, let's say it contains information for phone repair shops:

```sql
CREATE TABLE repair_center_table (repair_state VARCHAR PRIMARY KEY, long DOUBLE, lat DOUBLE)
       WITH (KAFKA_TOPIC='repair_center',
             PARTITIONS=1,
             VALUE_FORMAT='AVRO');
```

Now you also have a topic that contains insurance claim event data for people who have lost or damaged an insured phone:

```sql
CREATE STREAM insurance_event_stream (customer_name VARCHAR, phone_model VARCHAR, event VARCHAR,
                                      state VARCHAR, long DOUBLE, lat DOUBLE)
       WITH (KAFKA_TOPIC='phone_event_raw',
             PARTITIONS=1,
             VALUE_FORMAT='AVRO');
```

The goal is to route customers to the nearest repair shop.
In order to calculate how far away the repair center is from the insurance event, we will need to create a stream that joins the insurance events with our repair center reference data. or this use case, let’s assume there is only one repair center in each `STATE` and the repair center in an event’s `STATE` is the closest repair center.
 
```sql
CREATE STREAM insurance_event_with_repair_info AS
SELECT * FROM insurance_event_stream iev
INNER JOIN repair_center_table rct ON iev.state = rct.repair_state
EMIT CHANGES;
```

Now you have a stream that contains the lat-long of the repair shop and the phone damage event lat-long. 

The last step is to convert the two sets of lat-long coordinates to a distance:

```sql
SELECT iev_customer_name,
    iev_state,
    GEO_DISTANCE(iev_lat, iev_long, rct_lat, rct_long, 'miles') AS dist_to_repairer_km
FROM insurance_event_with_repair_info
EMIT CHANGES;
```

The `GEO_DISTANCE` function calculates the great-circle distance between two lat-long points, both specified in decimal degrees. An optional final parameter specifies km (the default) or miles.

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

  Run the following SQL statements to create the `repair_center_table` table and `insurance_event_stream` stream backed
  by Kafka running in Docker and populate them with test data.

  ```sql
  CREATE TABLE repair_center_table (repair_state VARCHAR PRIMARY KEY, long DOUBLE, lat DOUBLE)
         WITH (KAFKA_TOPIC='repair_center',
               PARTITIONS=1,
               VALUE_FORMAT='AVRO');
  ```

  ```sql
  INSERT INTO repair_center_table (repair_state, long, lat) VALUES ('NSW', 151.1169, -33.863);
  INSERT INTO repair_center_table (repair_state, long, lat) VALUES ('VIC', 145.1549, -37.9389);
  ```

  ```sql
  CREATE STREAM insurance_event_stream (customer_name VARCHAR, phone_model VARCHAR, event VARCHAR,
                                        state VARCHAR, long DOUBLE, lat DOUBLE)
         WITH (KAFKA_TOPIC='phone_event_raw',
               PARTITIONS=1,
               VALUE_FORMAT='AVRO');
  ```

  ```sql
  INSERT INTO insurance_event_stream (customer_name, phone_model, event, state, long, lat)
      VALUES ('Lindsey', 'iPhone 11 Pro', 'dropped', 'NSW', 151.25664, -33.85995);
  INSERT INTO insurance_event_stream (customer_name, phone_model, event, state, long, lat)
      VALUES ('Debbie', 'Samsung Note 20', 'water', 'NSW', 151.24504, -33.89640);
  ```

  Next, create a stream that joins the insurance events with our repair center reference data. Note that we first tell
  ksqlDB to consume from the beginning of the `insurance_event_stream` stream.
 
  ```sql
  SET 'auto.offset.reset'='earliest';
    
  CREATE STREAM insurance_event_with_repair_info AS
    SELECT * FROM insurance_event_stream iev
    INNER JOIN repair_center_table rct
    ON iev.state = rct.repair_state
    EMIT CHANGES;
  ```

  Finally, convert the two sets of lat-long coordinates to a distance:

  ```sql
  SELECT iev_customer_name,
      iev_state,
      GEO_DISTANCE(iev_lat, iev_long, rct_lat, rct_long, 'miles') AS dist_to_repairer_km
  FROM insurance_event_with_repair_info
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +-----------------------------+-----------------------------+-----------------------------+
  |IEV_CUSTOMER_NAME            |IEV_STATE                    |DIST_TO_REPAIRER_KM          |
  +-----------------------------+-----------------------------+-----------------------------+
  |Lindsey                      |NSW                          |8.020734621148486            |
  |Debbie                       |NSW                          |7.704588172240076            |
  Query Completed
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

  Enter the following statements in the editor and click `Run query`. This creates the `repair_center_table` table and
  `insurance_event_stream` stream and populates them with test data.

  ```sql
  CREATE TABLE repair_center_table (repair_state VARCHAR PRIMARY KEY, long DOUBLE, lat DOUBLE)
         WITH (KAFKA_TOPIC='repair_center',
               PARTITIONS=1,
               VALUE_FORMAT='AVRO');

  INSERT INTO repair_center_table (repair_state, long, lat) VALUES ('NSW', 151.1169, -33.863);
  INSERT INTO repair_center_table (repair_state, long, lat) VALUES ('VIC', 145.1549, -37.9389);

  CREATE STREAM insurance_event_stream (customer_name VARCHAR, phone_model VARCHAR, event VARCHAR,
                                        state VARCHAR, long DOUBLE, lat DOUBLE)
         WITH (KAFKA_TOPIC='phone_event_raw',
               PARTITIONS=1,
               VALUE_FORMAT='AVRO');

  INSERT INTO insurance_event_stream (customer_name, phone_model, event, state, long, lat)
      VALUES ('Lindsey', 'iPhone 11 Pro', 'dropped', 'NSW', 151.25664, -33.85995);
  INSERT INTO insurance_event_stream (customer_name, phone_model, event, state, long, lat)
      VALUES ('Debbie', 'Samsung Note 20', 'water', 'NSW', 151.24504, -33.89640);
  ```

  Next, create a stream that joins the insurance events with our repair center reference data. Paste in the following
  query and click `Run query`.
 
  ```sql
  CREATE STREAM insurance_event_with_repair_info AS
    SELECT * FROM insurance_event_stream iev
    INNER JOIN repair_center_table rct
    ON iev.state = rct.repair_state
    EMIT CHANGES;
  ```

  Finally, convert the two sets of lat-long coordinates to a distance:

  ```sql
  SELECT iev_customer_name,
      iev_state,
      GEO_DISTANCE(iev_lat, iev_long, rct_lat, rct_long, 'miles') AS dist_to_repairer_km
  FROM insurance_event_with_repair_info
  EMIT CHANGES;
  ```

  The query output should look like this:

  ```plaintext
  +-----------------------------+-----------------------------+-----------------------------+
  |IEV_CUSTOMER_NAME            |IEV_STATE                    |DIST_TO_REPAIRER_KM          |
  +-----------------------------+-----------------------------+-----------------------------+
  |Lindsey                      |NSW                          |8.020734621148486            |
  |Debbie                       |NSW                          |7.704588172240076            |
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
