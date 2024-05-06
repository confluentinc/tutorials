<!-- title: How to produce and consume Avro-formatted data the Apache Kafka &reg; Avro console tools  -->
<!-- description: In this tutorial, learn how to produce and consume Avro-formatted data the Apache Kafka &reg; Avro console tools. -->

# How to produce and consume Avro-formatted data the Apache Kafka &reg; Avro console tools

You'd like to produce and consume some basic messages, using (de)serializers and Schema Registry. In this tutorial, we'll show you how to produce and consume messages from the command line without any code.

## Setup

To produce records using Schema Registry, this tutorial assumes a local installation of Schema Registry or using Docker.

Producing records is very similar to using the console producer that ships with Kafka, instead you'll use the console producer that comes with `Schema Registry`:

```commandline
kafka-avro-console-producer \
  --topic <TOPIC> \
  --bootstrap-server <BOOTSTRAP-SERVER>:9092 \
  --property schema.registry.url <SCHEMA REGISTRY HOST>:8081 \
  --property value.schema="$(< PATH_TO_AVRO_SCHEMA.json)"
```

Then at the command prompt enter the values to send and hit enter:
```commandline
{"number":1,"shipping_address":"ABC Sesame Street,Wichita, KS. 12345","subtotal":110.00,"tax":10.00,"grand_total":120.00,"shipping_cost":0.00}
{"number":2,"shipping_address":"123 Cross Street,Irving, CA. 12345","subtotal":5.00,"tax":0.53,"grand_total":6.53,"shipping_cost":1.00}
{"number":3,"shipping_address":"5014  Pinnickinick Street, Portland, WA. 97205","subtotal":93.45,"tax":9.34,"grand_total":102.79,"shipping_cost":0.00}
{"number":4,"shipping_address":"4082 Elmwood Avenue, Tempe, AX. 85281","subtotal":50.00,"tax":1.00,"grand_total":51.00,"shipping_cost":0.00}
{"number":5,"shipping_address":"123 Cross Street,Irving, CA. 12345","subtotal":33.00,"tax":3.33,"grand_total":38.33,"shipping_cost":2.00}
```

This example assumes using Avro with the following schema:
```commandline
{
    "type": "record",
    "namespace": "io.confluent.tutorial",
    "name": "OrderDetail",
    "fields": [
        {"name": "number", "type": "long", "doc": "The order number."},
        {"name": "shipping_address", "type": "string", "doc": "The shipping address."},
        {"name": "subtotal", "type": "double", "doc": "The amount without shipping cost and tax."},
        {"name": "shipping_cost", "type": "double", "doc": "The shipping cost."},
        {"name": "tax", "type": "double", "doc": "The applicable tax."},
        {"name": "grand_total", "type": "double", "doc": "The order grand total."}
    ]
}
```

Then, to consume the records, use this command:
  
```commandline
kafka-avro-console-consumer \
  --topic <TOPIC> \
  --bootstrap-server <BOOTSTRAP-SERVER>:9092 \
  --property schema.registry.url=<SCHEMA REGISTRY HOST>:8081
```


