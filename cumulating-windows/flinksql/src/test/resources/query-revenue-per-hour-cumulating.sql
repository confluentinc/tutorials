SELECT ROUND(SUM(quantity * unit_price), 2) AS revenue,
       window_start,
       window_end
FROM TABLE(CUMULATE(TABLE orders, DESCRIPTOR(ts), INTERVAL '5' MINUTES, INTERVAL '10' MINUTES))
GROUP BY window_start, window_end;
