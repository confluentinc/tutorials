SELECT *
FROM temperature_readings
    MATCH_RECOGNIZE(
        PARTITION BY sensor_id
        ORDER BY ts ASC
        MEASURES
            FIRST(TEMP_SAME_DIRECTION.ts) AS firstTs,
            FIRST(TEMP_SAME_DIRECTION.temperature) AS firstTemp,
            LAST(TEMP_SAME_DIRECTION.ts) AS lastTs,
            LAST(TEMP_SAME_DIRECTION.temperature) AS lastTemp
        ONE ROW PER MATCH
        AFTER MATCH SKIP PAST LAST ROW
        PATTERN (TEMP_SAME_DIRECTION{5})
        DEFINE
          TEMP_SAME_DIRECTION AS
              (LAST(TEMP_SAME_DIRECTION.temperature, 1) IS NULL OR TEMP_SAME_DIRECTION.temperature > LAST(TEMP_SAME_DIRECTION.temperature, 1))
                AND (LAST(TEMP_SAME_DIRECTION.temperature, 2) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 1) > LAST(TEMP_SAME_DIRECTION.temperature, 2))
                AND (LAST(TEMP_SAME_DIRECTION.temperature, 3) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 2) > LAST(TEMP_SAME_DIRECTION.temperature, 3))
                AND (LAST(TEMP_SAME_DIRECTION.temperature, 4) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 3) > LAST(TEMP_SAME_DIRECTION.temperature, 4))
                AND (LAST(TEMP_SAME_DIRECTION.temperature, 5) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 4) > LAST(TEMP_SAME_DIRECTION.temperature, 5))
              OR
              (LAST(TEMP_SAME_DIRECTION.temperature, 1) IS NULL OR TEMP_SAME_DIRECTION.temperature < LAST(TEMP_SAME_DIRECTION.temperature, 1))
                AND (LAST(TEMP_SAME_DIRECTION.temperature, 2) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 1) < LAST(TEMP_SAME_DIRECTION.temperature, 2))
                AND (LAST(TEMP_SAME_DIRECTION.temperature, 3) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 2) < LAST(TEMP_SAME_DIRECTION.temperature, 3))
                AND (LAST(TEMP_SAME_DIRECTION.temperature, 4) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 3) < LAST(TEMP_SAME_DIRECTION.temperature, 4))
                AND (LAST(TEMP_SAME_DIRECTION.temperature, 5) IS NULL OR LAST(TEMP_SAME_DIRECTION.temperature, 4) < LAST(TEMP_SAME_DIRECTION.temperature, 5))
    ) MR;