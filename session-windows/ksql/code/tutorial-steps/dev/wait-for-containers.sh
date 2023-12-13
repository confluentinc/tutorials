#!/usr/bin/env bash

while : ; do
    ksqldb_status=$(curl -s -o /dev/null -w %{http_code} http://localhost:8088/info) 
    echo -e $(date) " ksqlDB server listener HTTP state: " $ksqldb_status 
    if [ $ksqldb_status -eq 200 ] ; then  
        break
    fi
    sleep 5 
done