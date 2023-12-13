docker run -it --network=host \
    -v ${PWD}/configuration/ccloud.properties:/tmp/configuration/ccloud.properties \
    edenhill/kcat:1.7.0 kcat \
         -F /tmp/configuration/ccloud.properties \
         -C -t test-topic \
         -e -q \
         | grep -v "Reading configuration from file" | wc -l
