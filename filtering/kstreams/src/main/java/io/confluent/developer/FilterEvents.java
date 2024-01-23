package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class FilterEvents {

    private static final Logger LOG = LoggerFactory.getLogger(FilterEvents.class);
    public static final String INPUT_TOPIC = "filtering-input";
    public static final String OUTPUT_TOPIC = "filtering-output";


    public Topology buildTopology(Properties allProps) {
        final StreamsBuilder builder = new StreamsBuilder();
        final Serde<Publication> publicationSerde = StreamsSerde.serdeFor(Publication.class);

        builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), publicationSerde))
                .peek((key, value) -> LOG.info("Incoming record key[{}] value[{}]", key, value))
                .filter((name, publication) -> "George R. R. Martin".equals(publication.name()))
                .peek((key, value) -> LOG.info("Filtered record key[{}] value[{}]", key, value))
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), publicationSerde));

        return builder.build(allProps);
    }
}
