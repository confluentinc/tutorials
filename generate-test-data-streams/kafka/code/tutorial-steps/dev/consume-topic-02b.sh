docker exec -i kcat kcat -b broker:9092 -t fleet_mgmt_sensors \
            -s key=s -s value=avro -r http://schema-registry:8081        \
            -f 'Key     (%K bytes):\t%k\nPayload (%S bytes):\t%s\n--\n' \
            -C -c5 -o end -u 2>/dev/null            
