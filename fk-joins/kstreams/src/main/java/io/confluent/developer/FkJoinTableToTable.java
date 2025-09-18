package io.confluent.developer;


import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class FkJoinTableToTable {

     private static final Logger LOG = LoggerFactory.getLogger(FkJoinTableToTable.class);
     public static final String ALBUM_TOPIC = "album-input";
     public static final String USER_TRACK_PURCHASE_TOPIC = "track-purchase";
     public static final String MUSIC_INTEREST_TOPIC = "music-interest";
    public Topology buildTopology(Properties envProps) {
        final StreamsBuilder builder = new StreamsBuilder();

        final Serde<String> stringSerde = Serdes.String();
        final Serde<MusicInterest> musicInterestSerde = StreamsSerde.serdeFor(MusicInterest.class);
        final Serde<Album> albumSerde = StreamsSerde.serdeFor(Album.class);
        final Serde<TrackPurchase> trackPurchaseSerde = StreamsSerde.serdeFor(TrackPurchase.class);

            final KTable<String, Album> albums = builder.table(ALBUM_TOPIC, Consumed.with(stringSerde, albumSerde));

            final KTable<String, TrackPurchase> trackPurchases = builder.table(USER_TRACK_PURCHASE_TOPIC, Consumed.with(stringSerde, trackPurchaseSerde));
            final MusicInterestJoiner trackJoiner = new MusicInterestJoiner();

            final KTable<String, MusicInterest> musicInterestTable = trackPurchases.join(albums,
                                                                                 TrackPurchase::albumId,
                                                                                 trackJoiner);

        musicInterestTable
                .toStream()
                .peek((key, value) -> LOG.info("Joined records key[{}] value[{}]", key, value))
                .to(MUSIC_INTEREST_TOPIC, Produced.with(stringSerde, musicInterestSerde));

        return builder.build(envProps);
    }


    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "fk-join-table-to-table");
        FkJoinTableToTable fkJoinTableToTable = new FkJoinTableToTable();

        Topology topology = fkJoinTableToTable.buildTopology(properties);

        try (KafkaStreams kafkaStreams = new KafkaStreams(topology, properties)) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                kafkaStreams.close(Duration.ofSeconds(5));
                countDownLatch.countDown();
            }));
            // For local running only don't do this in production as it wipes out all local state
            kafkaStreams.cleanUp();
            kafkaStreams.start();
            countDownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}