<!-- title: How to optimize a Kafka producer for throughput -->
<!-- description: In this tutorial, learn how to optimize a Kafka producer for throughput, with step-by-step instructions and supporting code. -->

# How to optimize a Kafka producer for throughput

When optimizing for Kafka producer performance, you'll typically need to consider tradeoffs between throughput and latency. Because of Kafka’s design, 
it isn't hard to write large volumes of data into it. But, many of Kafka's configuration parameters have default settings that optimize for latency. 
If your use case calls for higher throughput, then the following producer configuration parameters can be tuned to increase throughput. The values shown below are for demonstration purposes, and you will need to further tune these for your environment.

* `batch.size`: increase to 100000–200000 (default 16384)
* `linger.ms`: increase to 10–100 (default 0)
* `compression.type=lz4` (default `none`, i.e., no compression)
* `acks=1` (default `all`, since Apache Kafka version 3.0)

For a detailed explanation of these and other configuration parameters, read [these recommendations](https://www.confluent.io/resources/recommendations-developers-using-confluent-cloud) for Kafka developers.

## Running the example

Clone the `confluentinc/tutorials` GitHub repository (if you haven't already) and navigate to the `tutorials` directory:

```shell
git clone git@github.com:confluentinc/tutorials.git
cd tutorials
```

You can run the example backing this tutorial locally in Docker, or with Confluent Cloud.

<details>
  <summary>Docker</summary>

### Prerequisites

* Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
* [Docker Compose](https://docs.docker.com/compose/install/). Ensure that the command `docker compose version` succeeds.

### Run the commands

First, start Kafka:

  ```shell
  docker compose -f ./docker/docker-compose-kafka.yml up -d
  ```

Next, copy the `local.properties` producer configuration into the broker container:

  ```shell
  docker cp optimize-producer-throughput/kafka/local.properties broker:/etc/producer.properties
  ```

First, run a baseline performance test:

  ```shell
  docker exec broker  /usr/bin/kafka-producer-perf-test \
      --topic topic-perf \
      --num-records 10000 \
      --record-size 8000 \
      --throughput -1 \
      --producer.config /etc/producer.properties
  ```

And observe throughput (23.58 MB/sec in this example):

  ```shell
  10000 records sent, 3091.190108 records/sec (23.58 MB/sec), 927.27 ms avg latency, 1362.00 ms max latency, 949 ms 50th, 1302 ms 95th, 1352 ms 99th, 1360 ms 99.9th.
  ```

Now run the same test but with producer configuration tuned for higher throughput (94.89 MB / sec in the example output):

  ```shell
  docker exec broker  /usr/bin/kafka-producer-perf-test \
      --topic topic-perf \
      --num-records 10000 \
      --record-size 8000 \
      --throughput -1 \
      --producer.config /etc/producer.properties \
      --producer-props  \
          batch.size=200000 \
          linger.ms=100 \
          compression.type=lz4 \
          acks=1

  10000 records sent, 12437.810945 records/sec (94.89 MB/sec), 4.92 ms avg latency, 378.00 ms max latency, 3 ms 50th, 16 ms 95th, 38 ms 99th, 43 ms 99.9th.
  ```

</details>

<details>
  <summary>Confluent Cloud</summary>

### Prerequisites

* Docker running via [Docker Desktop](https://docs.docker.com/desktop/) or [Docker Engine](https://docs.docker.com/engine/install/)
* A [Confluent Cloud](https://confluent.cloud/signup) account

### Run the commands

First, create a cluster if you haven't already. You can do this in the Confluent Cloud Console by navigating to your environment and then clicking `Add cluster`.

Once you have a cluster running, navigate to `Topics` in the left-hand navigation and create a topic `topic-perf` with the default topic configuration.

Next, go to the Cluster Overview page and click `Clients` in the left-hand navigation. Click `Java` and generate a configuration file that includes API keys.
pool that you have created. Copy the configuration file locally to `optimize-producer-throughput/kafka/cloud.properties`.

Now, run a baseline performance test with Docker:

  ```shell
  docker run -v ./optimize-producer-throughput/kafka/cloud.properties:/etc/producer.properties confluentinc/cp-server:7.5.1 /usr/bin/kafka-producer-perf-test \
      --topic topic-perf \
      --num-records 10000 \
      --record-size 8000 \
      --throughput -1 \
      --producer.config /etc/producer.properties
  ```

And observe throughput (9.28 MB/sec in this example):

  ```shell
  10000 records sent, 1216.249088 records/sec (9.28 MB/sec), 2213.16 ms avg latency, 4665.00 ms max latency, 2098 ms 50th, 3550 ms 95th, 4344 ms 99th, 4640 ms 99.9th.
  ```
Now run the same test but with producer configuration tuned for higher throughput (15.24 MB / sec in the example output):

  ```shell
  docker run -v ./optimize-producer-throughput/kafka/cloud.properties:/etc/producer.properties confluentinc/cp-server:7.5.1 /usr/bin/kafka-producer-perf-test \
      --topic topic-perf \
      --num-records 10000 \
      --record-size 8000 \
      --throughput -1 \
      --producer.config /etc/producer.properties \
      --producer-props  \
          batch.size=200000 \
          linger.ms=100 \
          compression.type=lz4 \
          acks=1

  10000 records sent, 1997.602877 records/sec (15.24 MB/sec), 1172.76 ms avg latency, 1901.00 ms max latency, 1246 ms 50th, 1547 ms 95th, 1701 ms 99th, 1901 ms 99.9th.
  ```

</details>
