package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class JoinTableToTable {
     private static final Logger LOG = LoggerFactory.getLogger(JoinTableToTable.class);

     public static final String MOVIE_INPUT_TOPIC = "movie-input";
     public static final String RATING_INPUT_TOPIC = "ratings-input";
     public static final String RATED_MOVIES_OUTPUT = "rated-movies-output";


    public Topology buildTopology(Properties allProps) {
        
        final StreamsBuilder builder = new StreamsBuilder();
        final MovieRatingJoiner joiner = new MovieRatingJoiner();
        final Serde<Movie> movieSerde = new StreamsSerde<>(Movie.class);
        final Serde<Rating> ratingSerde = new StreamsSerde<>(Rating.class);
        final Serde<RatedMovie> ratedMovieSerde = new StreamsSerde<>(RatedMovie.class);

        KTable<Long, Movie> movieTable = builder.stream(MOVIE_INPUT_TOPIC,
                        Consumed.with(Serdes.Long(), movieSerde))
                .peek((key, value) -> LOG.info("Incoming movies key[{}] value[{}]", key, value))
                .toTable(Materialized.with(Serdes.Long(), movieSerde));

        KTable<Long, Rating> ratingsTable = builder.stream(RATING_INPUT_TOPIC,
                        Consumed.with(Serdes.Long(), ratingSerde))
                .peek((key, value) -> LOG.info("Incoming ratings key[{}] value[{}]", key, value))
                .map((key, rating) -> new KeyValue<>(rating.id(), rating))
                        .toTable(Materialized.with(Serdes.Long(), ratingSerde));

        ratingsTable.join(movieTable, joiner, Materialized.with(Serdes.Long(), ratedMovieSerde))
                .toStream()
                .peek((key, value) -> LOG.info("Table Joined results key[{}] value[{}]", key, value))
                .to(RATED_MOVIES_OUTPUT,
                        Produced.with(Serdes.Long(), ratedMovieSerde));

        return builder.build(allProps);
    }


    public static void main(String[] args) {
        Properties properties;
        if (args.length > 0) {
            properties = Utils.loadProperties(args[0]);
        } else {
            properties = Utils.loadProperties();
        }
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "join-table-to-table");
        JoinTableToTable joinTableToTable = new JoinTableToTable();

        Topology topology = joinTableToTable.buildTopology(properties);

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
