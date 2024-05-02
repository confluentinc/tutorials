<!-- title: How to mask data with ksqlDB -->
<!-- description: In this tutorial, learn how to mask data with ksqlDB, with step-by-step instructions and supporting code. -->

# How to mask data with ksqlDB

Suppose you have a topic that contains personally identifiable information (PII), and you want to mask those fields. In this tutorial, we'll write a program that persists the events in the original topic to a new Kafka topic with the PII removed or obfuscated.

## Setup

First, create a stream over the topic containing the PII data:

```sql
CREATE STREAM purchases (order_id INT, customer_name VARCHAR, date_of_birth VARCHAR,
                         product VARCHAR, order_total_usd DOUBLE, town VARCHAR, country VARCHAR)
    WITH (kafka_topic='purchases', value_format='json', partitions=1);
```
Then create a stream that will mask the PII columns using the ksqlDB [MASK](https://docs.ksqldb.io/en/0.8.1-ksqldb/developer-guide/ksqldb-reference/scalar-functions/#mask) function:

```sql

CREATE STREAM purchases_pii_obfuscated
    WITH (kafka_topic='purchases_pii_obfuscated', value_format='json', partitions=1) AS
    SELECT MASK(customer_name) AS CUSTOMER_NAME,
           MASK(date_of_birth) AS DATE_OF_BIRTH,
           order_id, product, order_total_usd, town, country
    FROM purchases;
```