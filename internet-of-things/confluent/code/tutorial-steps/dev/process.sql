SET 'auto.offset.reset' = 'earliest';

-- Create table with latest state of alarms
CREATE TABLE alarms (
  device_id VARCHAR PRIMARY KEY,
  alarm_name VARCHAR,
  code INT
) WITH (
  VALUE_FORMAT = 'JSON',
  KAFKA_TOPIC = 'alarms',
  PARTITIONS = 6
);

-- Create stream of throughputs 
CREATE STREAM throughputs (
  device_id VARCHAR KEY,
  throughput DOUBLE
) WITH (
  VALUE_FORMAT = 'JSON',
  KAFKA_TOPIC = 'throughputs',
  PARTITIONS = 6
);

-- Create new stream of critical issues to investigate
-- where throughputs are below threshold 1000.0 and alarm code is not 0
CREATE STREAM critical_issues WITH (KAFKA_TOPIC = 'critical_issues') AS
  SELECT
    t.device_id,
    t.throughput,
    a.alarm_name,
    a.code
  FROM throughputs t
  LEFT JOIN alarms a ON t.device_id = a.device_id
  WHERE throughput < 1000.0 AND a.code != 0;
