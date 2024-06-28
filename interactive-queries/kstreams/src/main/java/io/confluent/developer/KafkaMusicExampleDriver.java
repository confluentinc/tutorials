/*
 * Copyright Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.confluent.developer;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;



/**
 * This is a sample driver for the {@link KafkaMusicExample}.
 * To run this driver please first refer to the instructions in {@link KafkaMusicExample}.
 * You can then run this class directly in your IDE or via the command line.
 *
 * To run via the command line you might want to package as a fatjar first. Please refer to:
 * <a href='https://github.com/confluentinc/kafka-streams-examples#packaging-and-running'>Packaging</a>
 *
 * Once packaged you can then run:
 * <pre>
 * {@code
 * $ java -cp target/kafka-streams-examples-7.6.1-standalone.jar \
 *      io.confluent.developer.KafkaMusicExampleDriver
 * }
 * </pre>
 * You should terminate with Ctrl-C
 */
public class KafkaMusicExampleDriver {

  public static void main(final String [] args) throws Exception {
    final String bootstrapServers = args.length > 0 ? args[0] : "localhost:9092";
    System.out.println("Connecting to Kafka cluster via bootstrap servers " + bootstrapServers);

    List<Song> songs = new ArrayList<>();
    var lines = Files.readAllLines(Path.of("src/main/resources", "song_source.csv"));
    for (String line : lines) {
        final String[] values = line.split(",");
        songs.add( new Song(Long.parseLong(values[0]), values[1], values[2], values[3], values[4]));
    }

    final Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    
    final Serializer<PlayEvent> playEventSerializer = StreamsSerde.serdeFor(PlayEvent.class).serializer();
    final Serializer<Song> songSerializer = StreamsSerde.serdeFor(Song.class).serializer();

    final KafkaProducer<String, PlayEvent> playEventProducer = new KafkaProducer<>(props,
                                                                                   Serdes.String().serializer(),
                                                                                   playEventSerializer);

    final KafkaProducer<Long, Song> songProducer = new KafkaProducer<>(props,
                                                                       new LongSerializer(),
                                                                       songSerializer);

    songs.forEach(song -> {
      System.out.println("Writing song information for '" + song.name() + "' to input topic " +
          KafkaMusicExample.SONG_FEED);
      songProducer.send(new ProducerRecord<>(KafkaMusicExample.SONG_FEED, song.id(), song));
    });

    songProducer.close();
    final long duration = 60 * 1000L;
    final Random random = new Random();

    // send a play event every 100 milliseconds
    while (true) {
      final Song song = songs.get(random.nextInt(songs.size()));
      System.out.println("Writing play event for song " + song.name() + " to input topic " +
          KafkaMusicExample.PLAY_EVENTS);
      playEventProducer.send(
          new ProducerRecord<>(KafkaMusicExample.PLAY_EVENTS,
                                                "uk", new PlayEvent(song.id(), duration)));
      Thread.sleep(100L);
    }
  }

}
