SELECT device_id, lag_ms, window_start, window_end FROM iot_telemetry_lags WHERE lag_ms > 60000;
