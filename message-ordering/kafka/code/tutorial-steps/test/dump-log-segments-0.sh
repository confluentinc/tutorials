docker exec -t broker kafka-dump-log \
  --print-data-log \
  --files '/var/lib/kafka/data/myTopic-0/00000000000000000000.log' \
  --deep-iteration
