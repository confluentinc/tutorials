<!-- title: How to join on a foreign key in Kafka Streams -->
<!-- description: In this tutorial, learn how to join on a foreign key in Kafka Streams, with step-by-step instructions and supporting code. -->

# How to join on a foreign key in Kafka Streams

Suppose you are running an internet streaming music service where you offer albums or individual music tracks for sale. You'd like to track trends in listener preferences by joining the track purchases against the table of albums. The track purchase key doesn't align with the primary key for the album table, but since the value of the track purchase contains the ID of the album, you can extract the album ID from the track purchase and complete a foreign key join against the album table.

```java
final KTable<Long, Album> albums = builder.table(ALBUM_TOPIC, Consumed.with(longSerde, albumSerde));

final KTable<Long, TrackPurchase> trackPurchases = builder.table(USER_TRACK_PURCHASE_TOPIC, Consumed.with(longSerde, trackPurchaseSerde));

final MusicInterestJoiner trackJoiner = new MusicInterestJoiner();

final KTable<Long, MusicInterest> musicInterestTable = trackPurchases.join(albums,
                                                                           TrackPurchase::albumId,
                                                                           trackJoiner);
```

The foreign key join is made possible with the overloaded [KTable.join](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/kstream/KTable.html#join-org.apache.kafka.streams.kstream.KTable-java.util.function.Function-org.apache.kafka.streams.kstream.ValueJoiner-) method that takes a [Java Function](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/function/Function.html) that knows how to extract the foreign from the right-side records to perform 
the join with the left-side table.
