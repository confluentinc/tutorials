# Build a ksqlDB user-defined function (UDF) to transform events

Suppose you want to transform the values of a Kafka topic using a stateless scalar function not already provided by ksqlDB. As a concrete example,
consider a topic containing stock price events over which you want to calculate the [volume-weighted average price](https://en.wikipedia.org/wiki/Volume-weighted_average_price) (VWAP) for each event.
There is no built-in function for VWAP, so we'll write a custom [ksqlDB user-defined function](https://docs.ksqldb.io/en/latest/concepts/functions/#udfs) (UDF) in Java that performs the calculation.

## Setup

Imagine you have a stream of raw stock quotes created as follows:

```sql
CREATE STREAM raw_quotes(ticker VARCHAR KEY, bid DOUBLE, ask DOUBLE, bidqty INT, askqty INT)
    WITH (kafka_topic='stockquotes', value_format='avro', partitions=1);
```

The ksqlDB UDF Java API provides annotations to declare a UDF (its name and description) as well as its parameters and implementation:

```java
@UdfDescription(name = "vwap", description = "Volume-weighted average price")
public class VwapUdf {

    @Udf(description = "vwap for market prices as doubles, returns double")
    public double vwap(
            @UdfParameter(value = "bid")
            final double bid,
            @UdfParameter(value = "bidQty")
            final int bidQty,
            @UdfParameter(value = "ask")
            final double ask,
            @UdfParameter(value = "askQty")
            final int askQty) {
        return ((ask * askQty) + (bid * bidQty)) / (bidQty + askQty);
    }
}
```

# Installing a UDF

Once you've implemented a ksqlDB UDF, you need only build an uberjar containing it and then place the uberjar in the extensions path that you've configured
in ksqlDB server via the `ksql.extension.dir` property.

*_Note that Confluent Cloud does not support UDFs. This example runs ksqlDB in Docker._*


### Prerequisites

* Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
* Java 11 or later

### Run the commands

First, clone the `confluentinc/tutorials` repository:

```shell
git clone git@github.com:confluentinc/tutorials.git
```

Next, start ksqlDB and Kafka:

 ```shell
 docker compose -f ./docker/docker-compose-ksqldb-kraft-cluster.yml up -d
 ```

Build an uberjar containing the UDF class:

```shell
./gradlew clean :udf:ksql:shadowJar
```

Copy the uberjar onto the `ksqldb-server` container's file system. Specifically, copy it to the `/opt/` directory since that is the extensions directory configured in the Docker Compose file we used to start ksqlDB.

```shell
docker cp ./udf/ksql/build/libs/ksql-udf.jar ksqldb-server:/opt/ksql-udf.jar
```

Restart ksqlDB server:

```shell
docker restart ksqldb-server
```

Now let's enter the ksqlDB CLI to validate that the UDF was installed properly:

```shell
docker exec -it ksqldb-cli ksql http://ksqldb-server:8088
```

Let’s confirm the UDF jar has been loaded correctly. You will see `VWAP` in the list of functions:

```noformat
SHOW FUNCTIONS;
```

You can see some additional detail about the function with `DESCRIBE FUNCTION`:

```noformat
DESCRIBE FUNCTION VWAP;
```

The result gives you a description of the function including input parameters and the return type:

```noformat
Name        : VWAP
Overview    : Volume-weighted average price
Type        : SCALAR
Jar         : /opt/ksql-udf.jar
Variations  :

	Variation   : VWAP(bid DOUBLE, bidQty INT, ask DOUBLE, askQty INT)
	Returns     : DOUBLE
	Description : vwap for market prices as doubles, returns double
```

Let's see the UDF in action. First, create a Kafka topic and ksqlDB stream to represent stock quotes:

```sql
CREATE STREAM raw_quotes(ticker VARCHAR KEY, bid DOUBLE, ask DOUBLE, bidqty INT, askqty INT)
    WITH (kafka_topic='stockquotes', value_format='avro', partitions=1);
```

Then produce the following events to the stream:

```sql
INSERT INTO raw_quotes (ticker, bid, ask, bidqty, askqty) VALUES ('ZTEST', 15.00, 25.10, 100, 100);
INSERT INTO raw_quotes (ticker, bid, ask, bidqty, askqty) VALUES ('ZVV',   25.00, 35.25, 100, 100);
INSERT INTO raw_quotes (ticker, bid, ask, bidqty, askqty) VALUES ('ZVZZT', 35.00, 45.05, 100, 100);
INSERT INTO raw_quotes (ticker, bid, ask, bidqty, askqty) VALUES ('ZXZZT', 45.00, 55.12, 100, 100);

INSERT INTO raw_quotes (ticker, bid, ask, bidqty, askqty) VALUES ('ZTEST', 10.00, 20.91, 50, 100);
INSERT INTO raw_quotes (ticker, bid, ask, bidqty, askqty) VALUES ('ZVV',   30.00, 40.00, 100, 50);
INSERT INTO raw_quotes (ticker, bid, ask, bidqty, askqty) VALUES ('ZVZZT', 30.00, 40.10, 50, 100);
INSERT INTO raw_quotes (ticker, bid, ask, bidqty, askqty) VALUES ('ZXZZT', 50.00, 60.25, 100, 50);

INSERT INTO raw_quotes (ticker, bid, ask, bidqty, askqty) VALUES ('ZTEST', 15.00, 20.11, 100, 100);
INSERT INTO raw_quotes (ticker, bid, ask, bidqty, askqty) VALUES ('ZVV',   25.00, 35.34, 100, 100);
INSERT INTO raw_quotes (ticker, bid, ask, bidqty, askqty) VALUES ('ZVZZT', 35.00, 45.76, 100, 100);
INSERT INTO raw_quotes (ticker, bid, ask, bidqty, askqty) VALUES ('ZXZZT', 45.00, 55.49, 100, 100);
```

Now that you have stream with some events in it, let’s read them out. The first thing to do is set the following property to ensure that you’re reading from the beginning of the stream:

```sql
SET 'auto.offset.reset' = 'earliest';
```

Let’s invoke the `VWAP` function for every observed raw quote. Pay attention to the parameter ordering of the UDF.

```sql
SELECT ticker, vwap(bid, bidqty, ask, askqty) AS vwap FROM raw_quotes EMIT CHANGES LIMIT 12;
```

This should yield the following output:
```noformat
+-----------------------------------------------------------------------------------------------------+-----------------------------------------------------------------------------------------------------+
|TICKER                                                                                               |VWAP                                                                                                 |
+-----------------------------------------------------------------------------------------------------+-----------------------------------------------------------------------------------------------------+
|ZTEST                                                                                                |20.05                                                                                                |
|ZVV                                                                                                  |30.125                                                                                               |
|ZVZZT                                                                                                |40.025                                                                                               |
|ZXZZT                                                                                                |50.06                                                                                                |
|ZTEST                                                                                                |17.273333333333333                                                                                   |
|ZVV                                                                                                  |33.333333333333336                                                                                   |
|ZVZZT                                                                                                |36.733333333333334                                                                                   |
|ZXZZT                                                                                                |53.416666666666664                                                                                   |
|ZTEST                                                                                                |17.555                                                                                               |
|ZVV                                                                                                  |30.17                                                                                                |
|ZVZZT                                                                                                |40.38                                                                                                |
|ZXZZT                                                                                                |50.245                                                                                               |
Limit Reached
Query terminated
```

When you are finished exploring, clean up the containers used for this tutorial by running the following command from the top level of the `tutorials` repository:

```shell
docker compose -f ./docker/docker-compose-ksqldb-kraft-cluster.yml down
```
