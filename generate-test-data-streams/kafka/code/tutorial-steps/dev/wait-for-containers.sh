#!/bin/bash

# Wait for Schema Registry to become available
while : 
  do curl_status=$(curl -s -o /dev/null -w %{http_code} http://localhost:8081)
  echo -e $(date) " Component: Schema Registry\t\tHTTP state: " $curl_status "\t(waiting for 200)" 
  if [ $curl_status -eq 200 ] 
    then
      echo "✅✅ Schema Registry is ready"
      break
  fi
  sleep 5 
done

# Wait for Kafka Connect to become available
while : 
  do curl_status=$(curl -s -o /dev/null -w %{http_code} http://localhost:8083/)
  echo -e $(date) " Component: Kafka Connect \t\tHTTP state: " $curl_status "\t(waiting for 200)" 
  if [ $curl_status -eq 200 ] 
    then
      echo "✅✅ Kafka Connect is ready"
      break
  fi
  sleep 5 
done
