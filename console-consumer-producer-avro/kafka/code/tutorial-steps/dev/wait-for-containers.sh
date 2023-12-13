#!/bin/bash

# Wait for Schema Registry to become available
while :
  do curl_status=$(curl -s -o /dev/null -w %{http_code} http://localhost:8081)
  echo -e $(date) " Schema Registry HTTP state: " $curl_status " (waiting for 200)"
  if [ $curl_status -eq 200 ]
    then  break
  fi
  sleep 5
done
