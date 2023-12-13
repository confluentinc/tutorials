docker exec -t broker kafka-topics --bootstrap-server broker:9092 --topic myTopic --create --replication-factor 1 --partitions 2
