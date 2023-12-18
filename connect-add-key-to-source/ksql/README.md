# Add a key to data ingested through Kafka Connect


Kafka Connect is the integration API for Apache Kafka. It enables you to stream data from source systems (such as databases, message queues, SaaS platforms, and flat files) into Kafka, and from Kafka to target systems. When you stream data into Kafka, you often need to set the key correctly for partitioning and application logic reasons. In this example, we have a database containing data about cities, and we want to key the resulting Kafka messages by the city_id field. This tutorial will show you different ways of setting the key correctly. It will also cover how to declare the schema and use Kafka Streams to process the data using SpecificAvro.

## Setup

