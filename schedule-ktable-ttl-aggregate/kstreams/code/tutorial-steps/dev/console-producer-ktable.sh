docker exec -i broker kafka-console-producer --topic inputTopicForTable --bootstrap-server broker:9092 \
  --property parse.key=true \
  --property key.separator=":"
