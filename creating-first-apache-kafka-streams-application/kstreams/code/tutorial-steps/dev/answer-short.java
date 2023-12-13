static Topology buildTopology(String inputTopic, String outputTopic) {
    Serde<String> stringSerde = Serdes.String();
    StreamsBuilder builder = new StreamsBuilder();
    builder
        .stream(inputTopic, Consumed.with(stringSerde, stringSerde))
        .peek((k,v) -> logger.info("Observed event: {}", v))
        .mapValues(s -> s.toUpperCase())
        .peek((k,v) -> logger.info("Transformed event: {}", v))
        .to(outputTopic, Produced.with(stringSerde, stringSerde));
    return builder.build();
}
