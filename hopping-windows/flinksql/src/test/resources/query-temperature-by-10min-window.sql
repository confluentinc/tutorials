SELECT sensor_id,
    AVG(temperature) AS avg_temperature,
    window_start,
    window_end
FROM TABLE(HOP(TABLE temperature_readings, DESCRIPTOR(ts), INTERVAL '5' MINUTES, INTERVAL '10' MINUTES))
GROUP BY sensor_id, window_start, window_end;