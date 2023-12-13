# Confluent parallel consumer
The Confluent Parallel Consumer is an open source Apache 2.0-licensed Java library that enables you to consume from a Kafka topic with a higher degree of parallelism than the number of partitions for the input data (the effective parallelism limit achievable via an Apache Kafka consumer group). This is desirable in many situations, e.g., when partition counts are fixed for a reason beyond your control, or if you need to make a high-latency call out to a database or microservice while consuming and want to increase throughput.

In this tutorial, you'll first build a small "hello world" application that uses the Confluent Parallel Consumer library to read a handful of records from Kafka. Then you'll write and execute performance tests at a larger scale to compare the Confluent Parallel Consumer with a baseline built using a vanilla Apache Kafka consumer group.


## Hello World parallel consumer

For this introductory application, there is the `ParallelConsumerApplication`, which is the focal point of this tutorial; consuming records from a Kafka topic using the Confluent Parallel Consumer.


