package io.confluent.developer;

import org.apache.kafka.common.serialization.*;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;


class FkJoinTableToTableTest {


   @Test
   void testJoin() {
        final FkJoinTableToTable fkJoin = new FkJoinTableToTable();
        final Properties envProps = new Properties();

        final String albumInputTopic = FkJoinTableToTable.ALBUM_TOPIC;
        final String userPurchaseTopic = FkJoinTableToTable.USER_TRACK_PURCHASE_TOPIC;
        final String joinedResultOutputTopic = FkJoinTableToTable.MUSIC_INTEREST_TOPIC;

        final Topology topology = fkJoin.buildTopology(envProps);
        try (final TopologyTestDriver testDriver = new TopologyTestDriver(topology, envProps)) {

            final Serde<String> stringSerde = Serdes.String();
            final Serde<MusicInterest> musicInterestSerde = StreamsSerde.serdeFor(MusicInterest.class);
            final Serde<Album> albumSerde = StreamsSerde.serdeFor(Album.class);
            final Serde<TrackPurchase> trackPurchaseSerde = StreamsSerde.serdeFor(TrackPurchase.class);

            final Serializer<String> keySerializer = stringSerde.serializer();
            final Serializer<Album> albumSerializer = albumSerde.serializer();
            final Serializer<TrackPurchase> trackPurchaseSerializer = trackPurchaseSerde.serializer();
            final Deserializer<MusicInterest> musicInterestDeserializer =  musicInterestSerde.deserializer();

            final TestInputTopic<String, Album>  albumTestInputTopic = testDriver.createInputTopic(albumInputTopic, keySerializer, albumSerializer);
            final TestInputTopic<String, TrackPurchase> trackPurchaseInputTopic = testDriver.createInputTopic(userPurchaseTopic, keySerializer, trackPurchaseSerializer);
            final TestOutputTopic<String, MusicInterest> outputTopic = testDriver.createOutputTopic(joinedResultOutputTopic, new StringDeserializer(), musicInterestDeserializer);


            final List<Album> albums = getAlbums();
            final List<TrackPurchase> trackPurchases = getTrackPurchases();
            final List<MusicInterest> expectedMusicInterestJoinResults = getExpectedMusicInterestJoinResults();

            for (final Album album : albums) {
                albumTestInputTopic.pipeInput(album.id(), album);
            }

            for (final TrackPurchase trackPurchase : trackPurchases) {
                trackPurchaseInputTopic.pipeInput(trackPurchase.id(), trackPurchase);
            }

            final List<MusicInterest> actualJoinResults = outputTopic.readValuesToList();

            assertEquals(expectedMusicInterestJoinResults, actualJoinResults);
        }
    }

    private static List<Album> getAlbums() {
        final List<Album> albums = new ArrayList<>();
        albums.add(new Album("5", "Physical Graffiti", "Rock", "Led Zeppelin"));
        albums.add(new Album("6", "Highway to Hell", "Rock", "AC/DC"));
        albums.add(new Album("7", "Radio", "Hip hop", "LL Cool J"));
        albums.add(new Album("8", "King of Rock", "Rap rock", "Run-D.M.C"));
        return albums;
    }

    private static List<MusicInterest> getExpectedMusicInterestJoinResults() {
        final List<MusicInterest> expectedMusicInterestJoinResults = new ArrayList<>();
        expectedMusicInterestJoinResults.add(new MusicInterest("5-100", "Rock", "Led Zeppelin"));
        expectedMusicInterestJoinResults.add(new MusicInterest("8-101", "Rap rock", "Run-D.M.C"));
        expectedMusicInterestJoinResults.add(new MusicInterest("6-102", "Rock", "AC/DC"));
        expectedMusicInterestJoinResults.add(new MusicInterest("7-103", "Hip hop", "LL Cool J"));
        expectedMusicInterestJoinResults.add(new MusicInterest("8-104", "Rap rock", "Run-D.M.C"));
        expectedMusicInterestJoinResults.add(new MusicInterest("6-105", "Rock", "AC/DC"));
        expectedMusicInterestJoinResults.add(new MusicInterest("5-106", "Rock", "Led Zeppelin"));
        return expectedMusicInterestJoinResults;
    }

    private static List<TrackPurchase> getTrackPurchases() {
        final List<TrackPurchase> trackPurchases = new ArrayList<>();
        trackPurchases.add(new TrackPurchase("100", "Houses Of The Holy", "5", 0.99));
        trackPurchases.add(new TrackPurchase("101", "King Of Rock", "8", 0.99));
        trackPurchases.add(new TrackPurchase("102", "Shot Down In Flames", "6", 0.99));
        trackPurchases.add(new TrackPurchase("103", "Rock The Bells", "7", 0.99));
        trackPurchases.add(new TrackPurchase("104", "Can You Rock It Like This", "8", 0.99));
        trackPurchases.add(new TrackPurchase("105", "Highway To Hell", "6", 0.99));
        trackPurchases.add(new TrackPurchase("106", "Kashmir", "5", 0.99));
        return trackPurchases;
    }
}