# Handling ksqlDB deserialization errors

How can you identify and manage deserialization errors that cause some events from a Kafka topic to not be written into a stream or table?


## Setup 

During the development of event streaming applications, it is common to have situations where some streams or tables are not receiving some events that have been sent to them. Often this happens because there was a deserialization error due to the event not being in the right format, but that is not so trivial to figure out. In this tutorial, we'll write a program that monitors a stream of sensors. Any deserialization error that happens in this stream will be made available in another stream that can be queried to check errors.

With the [KSQL_PROCESSING_LOG](https://docs.ksqldb.io/en/latest/reference/processing-log/#processing-log) you can run a query like the following
to track any deserialization errors that may have occurred:

```sql
SELECT
    message->deserializationError->errorMessage,
    encode(message->deserializationError->RECORDB64, 'base64', 'utf8') AS MSG,
    message->deserializationError->cause
  FROM KSQL_PROCESSING_LOG
  EMIT CHANGES;
```


