docker run -v $PWD/configuration/ccloud.properties:/etc/ccloud.properties confluentinc/cp-server:7.3.0 /usr/bin/kafka-producer-perf-test \
    --topic topic-perf \
    --num-records 10000 \
    --record-size 8000 \
    --throughput -1 \
    --producer.config /etc/ccloud.properties \
    --producer-props \
        batch.size=200000 \
        linger.ms=100 \
        compression.type=lz4 \
        acks=1
