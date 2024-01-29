# Deduplicate events from a Kafka topic based on a field in the event with Kafka Streams

Consider a topic with events that represent clicks on a website. Each event contains an IP address, a URL, and a timestamp. In this tutorial, we'll write a program that filters duplicate click events by the IP address within a window of time.


```java
 builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), clicksSerde))
        .processValues(() -> new DeduplicationProcessor<>(windowSize.toMillis(), (key, value) -> value.ip()), STORE_NAME)
        .filter((k, v) -> v != null)
        .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), clicksSerde));
```
Note how the Kafka Streams topology uses a custom processor  the `DeduplicationProcessor` and a `Window Store`, to filter out the duplicate IP addresses. Events are de-duped within a 2-minute window, and unique clicks are produced to a new topic.

Let's take a look at the core logic of the `DeduplicationProcessor`:
```java
 public void process(FixedKeyRecord<K, V> fixedKeyRecord) {
            K key = fixedKeyRecord.key();
            V value = fixedKeyRecord.value();
            final E eventId = idExtractor.apply(key, value);
            if (eventId == null) {
                context.forward(fixedKeyRecord);  <1>
            } else {
                final V output;
                if (isDuplicate(eventId)) {
                    output = null;            <2>
                    updateTimestampOfExistingEventToPreventExpiry(eventId, context.currentStreamTimeMs());
                } else {
                    output = value;       <3>
                    rememberNewEvent(eventId, context.currentStreamTimeMs());
                }
                context.forward(fixedKeyRecord.withValue(output)); <4>
            }
        }
```
1. If the event id is not found, forward the record downstream.
2. If the record is a duplicate set the value to `null` and forward it and update the expiration timestamp. A downstream filter operator will remove the null value.
3. Otherwise, the record is not a duplicate, set the timestamp for expiration and forward the value.
4. The processor uses the`forward` method to send the record to the next processor in the topology.