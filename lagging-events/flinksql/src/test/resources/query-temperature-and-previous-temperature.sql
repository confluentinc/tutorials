SELECT sensor_id,
       ts,
       temperature,
       LAG(temperature, 1) OVER (PARTITION BY sensor_id ORDER BY ts) AS previous_temperature
FROM temperature_readings;
