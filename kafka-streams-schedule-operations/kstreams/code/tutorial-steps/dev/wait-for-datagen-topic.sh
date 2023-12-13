#!/bin/bash

# Wait for the datagen topic to be created
while : 
  do output=$(docker exec -t broker kafka-topics --bootstrap-server localhost:29092 --topic login-events --describe)
  if [[ "$output" =~ "Topic: login-events" ]]; then
    echo "Topic login-events exists"
    break
  fi
  sleep 5 
done
