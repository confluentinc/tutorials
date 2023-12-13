docker run --rm edenhill/kcat:1.7.1 \
    -X security.protocol=SASL_SSL -X sasl.mechanisms=PLAIN \
    -X ssl.ca.location=./etc/ssl/cert.pem -X api.version.request=true \
    -X sasl.username="${CCLOUD_API_KEY}" \
    -X sasl.password="${CCLOUD_API_SECRET}" \
    -b ${CCLOUD_BROKER_HOST}:9092 \
    -t my_topic \
    -C -e -q | \
    wc -l
