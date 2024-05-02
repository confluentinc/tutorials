<!-- title: How to compute an aggregate from multiple streams using cogrouping in Kafka Streams -->
<!-- description: In this tutorial, learn how to compute an aggregate from multiple streams using cogrouping in Kafka Streams, with step-by-step instructions and supporting code. -->

# How to compute an aggregate from multiple streams using cogrouping in Kafka Streams

Cogrouping is combining an aggregate, like `count`, from multiple streams into a single result.
In this tutorial, you will compute the count of user login events per application in your system, grouping the individual result from each source stream into one aggregated object using Kafka Streams [Cogroup](https://javadoc.io/static/org.apache.kafka/kafka-streams/3.6.1/org/apache/kafka/streams/kstream/CogroupedKStream.html#cogroup-org.apache.kafka.streams.kstream.KGroupedStream-org.apache.kafka.streams.kstream.Aggregator-) functionality.

```java
final KGroupedStream<String, LoginEvent> appOneGrouped =
        builder.stream(APP_ONE_INPUT_TOPIC, Consumed.with(stringSerde, loginEventSerde))
                .groupByKey();
final KGroupedStream<String, LoginEvent> appTwoGrouped =
        builder.stream(APP_TWO_INPUT_TOPIC, Consumed.with(stringSerde, loginEventSerde))
                .groupByKey();
final KGroupedStream<String, LoginEvent> appThreeGrouped =
        builder.stream(APP_THREE_INPUT_TOPIC, Consumed.with(stringSerde, loginEventSerde))
                .groupByKey();

final Aggregator<String, LoginEvent, LoginRollup> loginAggregator = new LoginAggregator();

        appOneGrouped.cogroup(loginAggregator)
                .cogroup(appTwoGrouped, loginAggregator)
                .cogroup(appThreeGrouped, loginAggregator)
                .aggregate(() -> new LoginRollup(new HashMap<>()), Materialized.with(Serdes.String(), loginRollupSerde))
        .toStream()
                .to(OUTPUT_TOPIC, Produced.with(stringSerde, loginRollupSerde));

```

Let's review the key points in this example

```java
final KGroupedStream<String, LoginEvent> appOneGrouped =
        builder.stream(APP_ONE_INPUT_TOPIC, Consumed.with(stringSerde, loginEventSerde))
                .groupByKey();
final KGroupedStream<String, LoginEvent> appTwoGrouped =
        builder.stream(APP_TWO_INPUT_TOPIC, Consumed.with(stringSerde, loginEventSerde))
                .groupByKey();
final KGroupedStream<String, LoginEvent> appThreeGrouped =
        builder.stream(APP_THREE_INPUT_TOPIC, Consumed.with(stringSerde, loginEventSerde))
                .groupByKey();
```

You have three input streams, and you need the intermediate object `KGroupedStream`, 
so you execute the `groupByKey()` method on each stream. 
For this tutorial, we have assumed the incoming records already have keys.

```java
Aggregator<String, LoginEvent, LoginRollup> loginAggregator = new LoginAggregator();
```
You’re using the cogrouping functionality here to get an overall grouping of logins per application. 
Kafka Streams creates this total grouping by using an `Aggregator` who knows how to extract records from each grouped stream. 
Your `Aggregator` instance here knows how to correctly combine each `LoginEvent` into the larger `LoginRollup` object.

```java
   appOneGrouped.cogroup(loginAggregator)
                .cogroup(appTwoGrouped, loginAggregator)
                .cogroup(appThreeGrouped, loginAggregator)
                .aggregate(() -> new LoginRollup(new HashMap<>()), Materialized.with(Serdes.String(), loginRollupSerde))
```

Now with your `KGroupedStream` objects, you start creating your larger aggregate by calling `KGroupedStream.cogroup()` 
on the first stream, using your `Aggregator`. This first step returns a `CogroupedKStream` instance. 
Then for each remaining `KGroupedStream`, you execute `CogroupedKSteam.cogroup()` using 
one of the `KGroupedStream` instances and the `Aggregator` you created previously. 
You repeat this sequence of calls for all the `KGroupedStream` objects you want to combine into an overall aggregate.

```java
       .toStream()
       .to(OUTPUT_TOPIC, Produced.with(stringSerde, loginRollupSerde));
```

The `aggregate` operation returns a `KTable` so you'll convert it to a `KStream` for producing the results out to a Kafka topic.

```java
public LoginRollup apply(final String appId,
                           final LoginEvent loginEvent,
                           final LoginRollup loginRollup) {
    final String userId = loginEvent.userId();
    final Map<String, Map<String, Long>> allLogins = loginRollup.loginByAppIdAndUserId();
    final Map<String, Long> userLogins = allLogins.computeIfAbsent(appId, key -> new HashMap<>());
    userLogins.compute(userId, (k, v) -> v == null ? 1L : v + 1L);
    return loginRollup;
```
The `Aggregator` you saw in the previous step constructs a map of maps: the count of logins per user, per application. 
What you see here is the core logic of the `LoginAggregator`.
Each call to `Aggregator.apply` retrieves the user login map for 
the given application id (or creates one if it doesn’t exist). 
From there, the `Aggregator` increments the login count for the given user.   
