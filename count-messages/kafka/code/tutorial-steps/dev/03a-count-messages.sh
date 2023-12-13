docker exec kcat \
    kcat -b broker:29092 -C -t pageviews -e -q | \
    wc -l