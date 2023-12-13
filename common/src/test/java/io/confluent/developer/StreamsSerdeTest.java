package io.confluent.developer;

import org.apache.kafka.common.serialization.Serde;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class StreamsSerdeTest {
    private final Serde<TestRecord> testRecordSerde = StreamsSerde.serdeFor(TestRecord.class);
    private final TestRecord expectedTestRecord = new TestRecord("X", 100, 500L);

    @Test
    @DisplayName("Serde should round trip a record")
    void roundTripMovieRatingTest() {
        String topic = "topic";
        byte[] serializedTestRecord = testRecordSerde.serializer().serialize(topic, expectedTestRecord);
        TestRecord actualTestRecord = testRecordSerde.deserializer().deserialize(topic, serializedTestRecord);
        assertThat(actualTestRecord, equalTo(expectedTestRecord));
    }
}
