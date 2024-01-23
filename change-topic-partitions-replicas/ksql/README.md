# Updating the number of partitions for a Kafka Topic

Imagine you want to change the partitions of your Kafka topic. You can use a streaming transformation to automatically stream all the messages from the original topic into a new Kafka topic that has the desired number of partitions or replicas.

## Setup

To accomplish this transformation, you'll create a stream based on the original topic:

```sql
 CREATE STREAM S1 (COLUMN0 VARCHAR KEY, COLUMN1 VARCHAR) WITH (KAFKA_TOPIC = 'topic1', VALUE_FORMAT = 'JSON');
```
Then you'll create a second stream that reads everything from the original topic and puts into a new topic with your desired number of 
partitions and replicas:

```sql
CREATE STREAM S2 WITH (KAFKA_TOPIC = 'topic2', VALUE_FORMAT = 'JSON', PARTITIONS = 2, REPLICAS = 2) AS SELECT * FROM S1;
```
Note that on Confluent Cloud handles settings such as `replicas` for you, so you can only specify replicas in an on-premise Kafka cluster.
