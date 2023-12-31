package io.confluent.developer;

import com.jasongoodwin.monads.Try;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;


public class PressureDatetimeExtractor implements TimestampExtractor {

    private final DateTimeFormatter formatter;
    private static final Logger logger = LoggerFactory.getLogger(TimestampExtractor.class);

    public PressureDatetimeExtractor() {
        this.formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.Z");
    }

    @Override
    public long extract(ConsumerRecord<Object, Object> record, long previousTimestamp) {
        return Try
                .ofFailable(() -> ((PressureAlert) record.value()).dateTime())
                .onFailure((ex) -> logger.error("fail to cast the PressureAlert: ", ex))
                .map((stringDatetimeString) ->  ZonedDateTime.parse(stringDatetimeString, this.formatter))
                .onFailure((ex) -> logger.error("fail to parse the event datetime due to: ", ex))
                .map((zonedDatetime) -> zonedDatetime.toInstant().toEpochMilli())
                .onFailure((ex) -> logger.error("fail to convert the datetime to instant due to: ", ex))
                .orElse(-1L);
    }
}
