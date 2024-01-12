# Read from specific offset and partition with the Apache Kafka &reg; console consumer



## Setup

You are confirming record arrivals, and you'd like to read from a specific offset in a topic partition. In this tutorial, you'll learn how to use the Apache Kafka console consumer to quickly debug issues by reading from a specific offset and/or partition.

By using the `--partition` and `--offset` flags available with the console consumer command you quickly validate message delivery to a given topic:

```commandline
kafka-console-consumer --topic <TOPIC> \
 --bootstrap-server <BOOTSTRAP-SERVER>:9092 \
 --property print.key=true \
 --property key.separator="-" \
 --partition <PARTITION NUMBER> \
 --offset <OFFSET>
```