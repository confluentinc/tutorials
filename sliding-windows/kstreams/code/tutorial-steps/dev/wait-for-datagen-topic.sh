#!/bin/bash

# Wait for the datagen topic to be created
while : 
  do output=$(docker exec -t broker kafka-topics --bootstrap-server localhost:29092 --topic temp-readings --describe)
  if [[ "$output" =~ "Topic: temp-readings" ]]; then
    echo "Topic temp-readings exists"
    break
  fi
  sleep 5 
done
