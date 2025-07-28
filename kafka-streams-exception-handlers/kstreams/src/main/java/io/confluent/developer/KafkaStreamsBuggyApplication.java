package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;

import java.util.Properties;


public class KafkaStreamsBuggyApplication {
    public Topology buildTopology(Properties allProps) {
        Serde<String> randomlyFailingStringSerde = Serdes.serdeFrom(new RandomlyFailingSerializer(), Serdes.String().deserializer());
        StreamsBuilder builder = new StreamsBuilder();

        builder.stream("input-topic", Consumed.with(Serdes.Integer(), Serdes.String()))
                .process(new RandomlyFailingProcessorSupplier())
                .to("output-topic", Produced.with(Serdes.Integer(), randomlyFailingStringSerde));

        Topology topology = builder.build(allProps);
        return topology;
    }
}
