SELECT o.id as order_id,
       FROM_UNIXTIME(o.order_ts_raw) as order_ts,
       o.total_amount as total,
       o.customer_name as customer,
       s.id as ship_id,
       FROM_UNIXTIME(s.ship_ts_raw) as ship_ts,
       s.warehouse,
       TIMESTAMPDIFF(HOUR,
           TO_TIMESTAMP(FROM_UNIXTIME(o.order_ts_raw)),
           TO_TIMESTAMP(FROM_UNIXTIME(s.ship_ts_raw))) as hr_to_ship
FROM orders o
INNER JOIN shipments s
    ON o.id = s.order_id
        AND TO_TIMESTAMP(FROM_UNIXTIME(s.ship_ts_raw))
            BETWEEN TO_TIMESTAMP(FROM_UNIXTIME(o.order_ts_raw))
            AND TO_TIMESTAMP(FROM_UNIXTIME(o.order_ts_raw))  + INTERVAL '7' DAY;
