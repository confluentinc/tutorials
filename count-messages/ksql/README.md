# Count the number of messages in a topic

It can be useful to know how many messages are currently in a topic, but you cannot calculate this directly based on the offsets, because you need to consider the topic's retention policy, log compaction, and potential duplicate messages. In this example, we'll take a topic of pageview data and see how we can count all the messages in the topic. Note that the time complexity for this tutorial is O(n) (linear); processing time will depend on the number of messages in the topic, and large data sets will require long running times.

## Setup

First, create a stream over the topic you're interested in counting the number of records.

```sql
CREATE STREAM pageviews (msg VARCHAR)
  WITH (KAFKA_TOPIC ='pageviews',
        VALUE_FORMAT='JSON');
```
Note that at this stage we’re just interested in counting the messages in their entirety, so we define the loosest schema possible, `msg VARCHAR`, for speed.
Also, you'll need to specify for ksql start from the beginning of the topic so that all messages are included in the count:
```text
SET 'auto.offset.reset' = 'earliest';
```

Then create a table for the count by selecting from the `pageviews` stream:

```sql
CREATE TABLE MSG_COUNT AS
    SELECT 'X' AS X,
        COUNT(*) AS MSG_CT
    FROM PAGEVIEWS
    GROUP BY 'X'
    EMIT CHANGES;
```
The query above will run continually, until you cancel it (or if you included a `LIMIT` clause). But if you want more of a "point in time" result, you can issue a [pull query ](https://docs.ksqldb.io/en/latest/concepts/queries/#pull) like this:

```sql
SELECT * FROM MSG_COUNT WHERE X='X';
```