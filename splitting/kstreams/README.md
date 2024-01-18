# How to split a stream of events into substreams with Kafka Streams

If you have a stream of events in a Kafka topic and wish to route those events to different topics based on data in the events, [KStream.split](https://kafka.apache.org/36/javadoc/org/apache/kafka/streams/kstream/KStream.html#split()) and [BranchedKStream.branch](https://kafka.apache.org/36/javadoc/org/apache/kafka/streams/kstream/KStream.html#split()) can be used to route the source topic's events.

For example, suppose that you have a Kafka topic representing appearances of an actor or actress in a film, with each event also containing the movie genre. The following topology definition will split the stream into three substreams: one containing drama events, one containing fantasy, and one containing events for all other genres:

``` java
    builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), actingEventSerde))
            .split()
            .branch((key, appearance) -> "drama".equals(appearance.genre()),
                    Branched.withConsumer(ks -> ks.to(OUTPUT_TOPIC_DRAMA)))
            .branch((key, appearance) -> "fantasy".equals(appearance.genre()),
                    Branched.withConsumer(ks -> ks.to(OUTPUT_TOPIC_FANTASY)))
            .branch((key, appearance) -> true,
                    Branched.withConsumer(ks -> ks.to(OUTPUT_TOPIC_OTHER)));
```
