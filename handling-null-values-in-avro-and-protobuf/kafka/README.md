# How to allow `null` field values in Avro and Protobuf

Let's say you're using an Avro or Protobuf schema, and sometimes you want to set a field named `item` to null. Say it's a pipeline that takes both donations and purchases to be filtered later, and the donations are processed as purchases with null items. How to adjust the schema to allow for a null value? 

Avro natively supports null fields with the 'null' type. In the above example, in order to make the `item` field nullable, you can allow the type to be "string" or "null" in the following manner:

```
{"name": "item", "type": ["string", "null"] }
```

In Protobuf, null values that occur due to the item not being set are handled automatically. But if you want to explicitly set the item to null, you'd have to use a [wrapper](https://tomasbasham.dev/development/2017/09/13/protocol-buffers-and-optional-values.html).


Let's walk through some pertinent bits of the code before running it. 

In this tutorial let's say we're tracking purchase events in Kafka with Confluent Cloud, each with an `item`, a `total_cost`, and a `customer_id`. 


In the `AvroProducer.java` file, there's a Kafka producer to send purchase events to a Kafka topic:

```java
List<PurchaseAvro> avroPurchaseEvents = new ArrayList<>();

            try (final Producer<String, PurchaseAvro> producer = new KafkaProducer<>(avroProducerConfigs)) {
                String avroTopic = "avro-purchase";

                PurchaseAvro avroPurchase = getPurchaseObjectAvro(purchaseBuilder);
                PurchaseAvro avroPurchaseII = getPurchaseObjectAvro(purchaseBuilder);

                avroPurchaseEvents.add(avroPurchase);
                avroPurchaseEvents.add(avroPurchaseII);

                avroPurchaseEvents.forEach(event -> producer.send(new ProducerRecord<>(avroTopic, event.getCustomerId(), event), ((metadata, exception) -> {
                    if (exception != null) {
                        System.err.printf("Producing %s resulted in error %s %n", event, exception);
                    } else {
                        System.out.printf("Produced record to topic with Avro schema at offset %s with timestamp %d %n", metadata.offset(), metadata.timestamp());
                    }
                })));


            }
            return avroPurchaseEvents;
        }
```

In this file, we're setting the `item` in each event explicitly to `null`:

```java
        PurchaseAvro getPurchaseObjectAvro(PurchaseAvro.Builder purchaseAvroBuilder) {
            purchaseAvroBuilder.setCustomerId("Customer Null").setItem(null)
                    .setTotalCost(random.nextDouble() * random.nextInt(100));
            return purchaseAvroBuilder.build();
        }
```

In the `AvroConsumer.java` file, those events are consumed and printed to the console:

```java
avroConsumer.subscribe(Collections.singletonList("avro-purchase"));

            ConsumerRecords<String, PurchaseAvro> avroConsumerRecords = avroConsumer.poll(Duration.ofSeconds(2));
            avroConsumerRecords.forEach(avroConsumerRecord -> {
                PurchaseAvro avroPurchase = avroConsumerRecord.value();
                System.out.print("Purchase details consumed from topic with Avro schema { ");
                System.out.printf("Customer: %s, ", avroPurchase.getCustomerId());
                System.out.printf("Total Cost: %f, ", avroPurchase.getTotalCost());
                System.out.printf("Item: %s } %n", avroPurchase.getItem());

            });

```

## Running the example

You can run this example either with Confluent Cloud or by running the unit test. Before getting started with either method,
clone `https://github.com/confluentinc/tutorials.git` and `cd` into `tutorials/handling-null-values-in-avro-and-protobuf`.

<details>
  <summary>Kafka Streams-based test</summary>

#### Prerequisites

* Java 17, e.g., follow the OpenJDK installation instructions [here](https://openjdk.org/install/) if you don't have Java. 

#### Run the test

From the top-level directory:

```
./gradlew clean :handling-null-values-in-avro-and-protobuf:kafka:test --info  
```

<details>
  <summary>Confluent Cloud</summary>

#### Prerequisites

  * A [Confluent Cloud](https://confluent.cloud/signup) account

#### Run the commands

[Sign up](https://www.confluent.io/) for a Confluent Cloud account if you haven't already. 

Login, and then click 'Environments -> Create Cloud Environment' and create a cloud environment using the defaults there. 

Navigate to your environment and click 'Add cluster'. Create a cluster using the default values provided. 

Click 'Topics -> Add topic' to create two topics with the default values, one named 'avro-purchase' and the other 'proto-purchase' (we'll cover null values in Protobuf schemas later in the tutorial). 

On the right-hand navbar, click 'API keys -> Add key -> Global access'. Download the values as you will need them to run this tutorial. 

In the same navbar, click 'Clients -> Choose Your Language -> Java -> Create Schema Registry API key'. Save this key and secret as well as the URL listed in the configuration snippet. 

Now, create a file at `handling-null-values/resources/confluent.properties` with these values in it:

```
# Required connection configs for Kafka producer, consumer, and admin
bootstrap.servers=BOOTSTRAP_URL/S
security.protocol=SASL_SSL
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username='USERNAME' password='PASSWORD';
sasl.mechanism=PLAIN
use.latest.version=true
# Required for correctness in Apache Kafka clients prior to 2.6
client.dns.lookup=use_all_dns_ips

wrapper.for.nullables=true
key.converter=io.confluent.connect.avro.AvroConverter
key.converter.schema.registry.url=SR_URL/S
value.converter=io.confluent.connect.avro.AvroConverter
value.converter.schema.registry.url=CONVERTER_SR_URL/S

# Best practice for higher availability in Apache Kafka clients prior to 3.0
session.timeout.ms=45000

# Best practice for Kafka producer to prevent data loss
acks=all

# Required connection configs for Confluent Cloud Schema Registry
schema.registry.url=SR_URL/S
basic.auth.credentials.source=USER_INFO
basic.auth.user.info=API_KEY:SECRET
```

Replace the USERNAME and PASSWORD values with the Confluent Cloud key and secret respectively. Add the url from the schema registry client configuration snippet for `SR_URL/S` and add the schema registry API key and secret for `basic.auth.user.info`, retaining the colon in the placeholder. 

Inside `handling-null-values/kafka/code/src/main/avro/purchase.avsc` you'll see: 

```
{
  "type":"record",
  "namespace": "io.confluent.developer.avro",
  "name":"PurchaseAvro",
  "fields": [
    {"name": "item", "type": ["string", "null"] },
    {"name": "total_cost", "type": "double" },
    {"name": "customer_id", "type": "string"}
  ]
}
```

When you run `gradle runAvroProducer` and furthermore, `gradle runAvroConsumer`, you'll see that the events with null items are produced and consumed successfully. 

Now remove the `["string", "null"]` in the first field and replace it with `"string"`:

```
{
  "type":"record",
  "namespace": "io.confluent.developer.avro",
  "name":"PurchaseAvro",
  "fields": [
    {"name": "item", "type": "string" },
    {"name": "total_cost", "type": "double" },
    {"name": "customer_id", "type": "string"}
  ]
}
```

Now, if you run the code using `gradle runAvroProducer`, you will see that the producer does not produce events. If Avro schemas are to accept null values they need it set explicitly on the field.

How about null values in Protobuf schema fields? See: `handling-null-values/kafka/code/src/main/proto/purchase.proto`:

```
syntax = "proto3";

package io.confluent.developer.proto;
option java_outer_classname = "PurchaseProto";

message Purchase {
  string item = 1;
  double total_cost = 2;
  string customer_id = 3;
}
```

Look at `ProtoProducerApp.java`, lines 76-77:

```java
        purchaseBuilder.setCustomerId("Customer Null")
                .setTotalCost(random.nextDouble() * random.nextInt(100));
``` 

We can see that the developer who wrote this app 'forgot' to write the `setItem()` method that adds an item. This means that the value will be null. But when you run you run `gradle runProtoProducer` and `gradle runProtoConsumer` no errors will arise. That's because Protobuf automatically handles default values.

The message will look something like this in Confluent Cloud:

```json
{
  "totalCost": 41.20575583194131,
  "customerId": "Customer Null"
}
```

and like this in the console:

```json
{ Customer: Customer Null, Total Cost: 21.075714, Item:  } 

```

Now, if you _explicitly_ set the value of the item to null like so:


```java
        purchaseBuilder.setCustomerId("Customer Null").setItem(null)
                .setTotalCost(random.nextDouble() * random.nextInt(100));
``` 

In this case, you'll receive a NullPointer error. You can allow null values to be explicitly set with a [protocol wrapper type](https://protobuf.dev/reference/protobuf/google.protobuf/https://protobuf.dev/reference/protobuf/google.protobuf/).