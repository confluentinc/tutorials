#!/bin/bash

# Wait for ksqlDB to become available
while :
  do curl_status=$(curl -s -o /dev/null -w %{http_code} http://localhost:8088/info)
  echo -e $(date) " Component: ksqlDB \t\t\tHTTP state: " $curl_status "\t(waiting for 200)"
  if [ $curl_status -eq 200 ]
    then
      echo "✅ ksqlDB is ready"
      # Attempt to avoid flaky test failures
      sleep 1
      break
  fi
  sleep 5
done
