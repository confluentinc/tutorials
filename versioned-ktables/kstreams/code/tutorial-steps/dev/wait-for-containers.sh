#!/bin/bash

function readiness_probe {
    nc -z -w 2 0.0.0.0 9092
}

echo "Waiting for the broker to become available ..."

readiness_probe

while [[ $? != 0 ]]; do
    sleep 5
    readiness_probe
done
