<!-- title: How to achieve temporal join accuracy in Kafka Streams with versioned KTables -->
<!-- description: In this tutorial, learn how to achieve temporal join accuracy in Kafka Streams with versioned KTables, with step-by-step instructions and supporting code. -->

# How to achieve temporal join accuracy in Kafka Streams with versioned KTables

Proper handling of time in Kafka Stream stream-table joins has historically been difficult to achieve. It used to be when 
Kafka Streams executes a stream-table join the stream side event would join the latest available record with the same key on the table side.
But, sometimes it's important for the stream event to match up with a table record by timestamp as well as key.
Consider a stream of stock transactions and a table of stock prices -- it's essential the transaction joins with the 
stock price at the time of the transaction, not the latest price. A versioned state store tracks multiple record versions 
for the same key, rather than the single latest record per key, as is the case for standard non-versioned stores.

The key to versioned state stores is to use a `VersionedKeyValueStore` when creating a `KTable`:
``` java annotate
    final VersionedBytesStoreSupplier versionedStoreSupplier =
              Stores.persistentVersionedKeyValueStore("versioned-ktable-store",
                                                       Duration.ofMinutes(10));


    final KTable<String, String> tableInput = builder.table(tableInputTopic,
                Materialized.<String, String>as(versionedStoreSupplier)
                        .withKeySerde(stringSerde)
                        .withValueSerde(stringSerde));
```
Assuming you have a versioned `KTable` and a `KStream` with out-of-order records to join, the join will be temporally correct since each stream record with be joined
with a table record _aligned by timestamp_ instead of simply using the latest record for the key.
