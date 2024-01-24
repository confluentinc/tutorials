# Filtering

How do you filter messages in a Kafka topic to contain only those that you're interested in?


## Setup

First, you'll create a stream based on the topic you're interested in only seeing a subset of the records.

```sql
 CREATE STREAM all_publications (bookid BIGINT KEY, 
                                 author VARCHAR, 
                                 title VARCHAR)
    WITH (kafka_topic = 'publication_events', value_format = 'JSON');
```

In this case, it's a topic containing with events representing book publications. You want a query that creates a new topic containing only events for a particular author.

```sql
CREATE STREAM george_martin WITH (kafka_topic = 'george_martin_books') AS
    SELECT *
      FROM all_publications
      WHERE author = 'George R. R. Martin';
```

Now you have a new stream that will place the filtered results in a new topic.