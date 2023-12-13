docker exec -i kcat kcat -b broker:9092 -t postgres_cities \
            -C -s avro -r http://schema-registry:8081 -e \
            -f 'Key     (%K bytes):\t%k\nPayload (%S bytes):\t%s\n--\n'
