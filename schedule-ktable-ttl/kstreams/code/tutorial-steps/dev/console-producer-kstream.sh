docker exec -i broker kafka-console-producer --topic inputTopicForStream --bootstrap-server broker:9092 \
  --property parse.key=true \
  --property key.separator=":"
