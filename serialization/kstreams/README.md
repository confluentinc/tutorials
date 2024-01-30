# How to change the serialization format of messages with Kafka Streams

If you have a stream of Avro-formatted events in a Kafka topic, Kafka Streams makes it very easy to convert the format to Protobuf.

For example, suppose that you have a Kafka topic representing movie releases. By specifying that event values should be consumed with the Avro deserializer and produced to the output topic with the Protobuf serializer,
all that's needed is a [map](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.0/org/apache/kafka/streams/kstream/KStream.html#map-org.apache.kafka.streams.kstream.KeyValueMapper-) operation in which the Protobuf object to be used as the value is constructed from the given Avro object:

``` java
    builder.stream(INPUT_TOPIC, Consumed.with(Long(), movieSpecificAvroSerde))
           .map((key, avroMovie) ->
                   new KeyValue<>(key, MovieProtos.Movie.newBuilder()
                           .setMovieId(avroMovie.getMovieId())
                           .setTitle(avroMovie.getTitle())
                           .setReleaseYear(avroMovie.getReleaseYear())
                           .build()))
           .to(OUTPUT_TOPIC, Produced.with(Long(), movieProtoSerde));
```
