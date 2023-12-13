SELECT iev_customer_name, iev_state,
       geo_distance(iev_lat, iev_long, rct_lat, rct_long, 'km') AS dist_to_repairer_km
FROM insurance_event_with_repair_info
EMIT CHANGES
LIMIT 2;