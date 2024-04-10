SELECT window_start, window_end, ip_address, url
FROM (
         SELECT window_start, window_end, ip_address, url,
                ROW_NUMBER() OVER ( PARTITION BY window_start, window_end, ip_address, url ORDER BY ts ) AS rownum
         FROM TABLE(TUMBLE(TABLE clicks, DESCRIPTOR(ts), INTERVAL '1' HOUR))
     )
WHERE rownum <= 1;
