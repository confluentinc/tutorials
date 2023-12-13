SELECT ip_address, url, FROM_UNIXTIME(click_ts_raw) as click_timestamp
FROM (
        SELECT *,
               ROW_NUMBER() OVER ( PARTITION BY ip_address ORDER BY TO_TIMESTAMP(FROM_UNIXTIME(click_ts_raw)) ) as rownum FROM clicks
     )
WHERE rownum = 1;
