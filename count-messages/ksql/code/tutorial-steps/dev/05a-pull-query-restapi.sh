docker exec ksqldb \
    curl --silent --show-error \
         --http2 'http://localhost:8088/query-stream' \
         --data-raw '{"sql":"SELECT MSG_CT FROM MSG_COUNT WHERE X='\''X'\'';"}'