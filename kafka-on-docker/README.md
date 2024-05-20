<!-- title: How to run Kafka locally on Docker -->
<!-- description: In this tutorial, learn how to run Kafka broker locally on your laptop, with step-by-step instructions and supporting code. -->

# How to run a Kafka broker locally with Docker

When building or testing Apache®Kafka applications, it can be very helpful to have a local Kafka broker running locally.  Using [Docker](https://www.docker.com/) is possibly the fastest way to get a locally running Kafka broker

## Set up

To run Kafka on Docker, you'll need to install [Docker Desktop](https://www.docker.com/products/docker-desktop/). Then, after you've installed Docker Desktop, you'll use [Docker Compose](https://docs.docker.com/compose/) to create your Kafka container.  Docker Compose uses a YAML configuration file to manage your Docker components (services, volumes, networks, etc.) in an easy-to-maintain approach.  Docker Desktop includes Docker Compose, so there are no additional steps you need to take.
Finally, you'll need an account on [docker hub](https://hub.docker.com/explore) so Docker Desktop can pull the images specified in the Docker Compose file.
  
## Running Kafka in Docker

Before you get started, here's the Docker Compose file for the tutorial:

```yaml
services:
  broker:
    image: apache/kafka:latest
    hostname: broker
    container_name: broker
    ports:
      - 9092:9092
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://broker:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_NODE_ID: 1
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@broker:29093
      KAFKA_LISTENERS: PLAINTEXT://broker:29092,CONTROLLER://broker:29093,PLAINTEXT_HOST://0.0.0.0:9092
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LOG_DIRS: /tmp/kraft-combined-logs
      CLUSTER_ID: MkU3OEVBNTcwNTJENDM2Qk
```
Let's review some of the key parts of the YAML 

```yaml
 broker:
    image: apache/kafka:latest
    hostname: broker
    container_name: broker
    ports:
      - 9092:9092
```
 - `image`  contains the organization, image name and the version to use.  In this case it will always pull the latest one available.
 - `ports` specifies the ports you use to connect to Kafka.

```yaml
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
```
These settings affect topic replication and min-in-sync replicas and should only ever use these values when running a single Kafka broker on Docker. For a full explanation of these and other configurations,
[consult the Kafka documentation](https://docs.confluent.io/platform/current/installation/configuration/broker-configs.html#cp-config-brokers).