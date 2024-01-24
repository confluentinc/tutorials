# Filtering records in Kafka Streams

Consider a topic with events, and you want to filter out records not matching a given attribute.

```java
 builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), publicationSerde))
        .filter((name, publication) -> "George R. R. Martin".equals(publication.name()))
        .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), publicationSerde));
```

To keep only records in the event stream matching a given predicate (either the key or the value), you'll use the [KStream.filter](https://kafka.apache.org/36/javadoc/org/apache/kafka/streams/kstream/KStream.html#filter(org.apache.kafka.streams.kstream.Predicate)).  
For retaining records that 
__*do not*__ match a predicate you can use [KStream.filterNot](https://kafka.apache.org/36/javadoc/org/apache/kafka/streams/kstream/KStream.html#filterNot(org.apache.kafka.streams.kstream.Predicate))