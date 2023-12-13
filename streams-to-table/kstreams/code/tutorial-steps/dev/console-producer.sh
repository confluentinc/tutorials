kafka-console-producer --topic input-topic --bootstrap-server broker:9092 \
  --property parse.key=true \
  --property key.separator=":"