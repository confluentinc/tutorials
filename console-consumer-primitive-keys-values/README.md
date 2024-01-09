# Consuming non-string primitive keys and values with the Apache Kafka &reg; console consumer 

You want to inspect or debug records written to a topic. Each record key and value is a long and double, respectively. In this tutorial, you'll learn how to specify key and value deserializers with the Apache Kafka console consumer.


## Setup

To consume records from the command-line, cd into the <KAFKA BASE DIR>/bin directory. Then execute the following command (assuming keys with a type of `Long` and values of `Double`).

If you try to consume records without specifying a deserializer like so:
```commandline
kafka-console-consumer --topic <TOPIC>\
 --bootstrap-server <BOOTSTRAP-SERVER>:9092 \
 --from-beginning \
 --max-messages 10 \ 
 --property print.key=true \
 --property key.separator=" : " \
```

You'll see results that look like the following because the default string deserializer is used:

```commandline
!? : @'?u_?mY
J? : ?(?,???
?c : @T?????
?? : @S{??ދ
?? : @F!?u??
? : ??{??%??
#f : @S??
?A
 : ?T5Ni?^?
 : ?κ?e
 : @>ֈ&???
```

Now update the command to include a deserializer for the key and value:
```commandline
kafka-console-consumer --topic <TOPIC>\
 --bootstrap-server <BOOTSTRAP-SERVER>:9092 \
 --from-beginning \
 --property print.key=true \
 --property key.separator=" : " \
 --max-messages 10 \ 
 --key-deserializer "org.apache.kafka.common.serialization.LongDeserializer" \
 --value-deserializer "org.apache.kafka.common.serialization.DoubleDeserializer"
```
Now, your results will look like:
 
```commandline
7000546592895906471 : 0.6322582597613734
-2815276123070627191 : 0.3391308127269156
-6910239382365155685 : 0.8764180508020841
-1108990429618029888 : 0.9034900159780727
-6051646600310729054 : 0.6484490061426863
-297855922771180036 : 0.6636920219701741
-2161765026709818701 : 0.6839825636436166
4577207098168959186 : 0.9256904333195043
7666900789317051211 : 0.26556161712153203
2627408892797753421 : 0.9715509777797698
Processed a total of 10 messages
```