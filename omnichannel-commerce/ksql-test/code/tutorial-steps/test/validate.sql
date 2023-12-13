SET 'auto.offset.reset' = 'earliest';

SELECT SHOE_ORDERS_ORDER_ID AS ID FROM shoe_orders_enriched EMIT CHANGES LIMIT 5;
