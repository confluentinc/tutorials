#!/usr/bin/env bash

docker exec ksqldb ksql-test-runner -i /opt/app/test/input.json -s /opt/app/src/statements.sql -o /opt/app/test/output.json
