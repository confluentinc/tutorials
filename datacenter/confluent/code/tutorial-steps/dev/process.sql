SET 'auto.offset.reset' = 'earliest';

-- Create a Table for the captured tenant occupancy events
CREATE TABLE tenant_occupancy (
  tenant_id VARCHAR PRIMARY KEY,
  customer_id BIGINT
) WITH (
  KAFKA_TOPIC = 'tenant-occupancy',
  PARTITIONS = 6,
  KEY_FORMAT = 'JSON',
  VALUE_FORMAT = 'JSON'
);

-- Create a Stream for the power control panel telemetry data.
--   tenant_kwh_usage is reset by the device every month
CREATE STREAM panel_power_readings (
  panel_id BIGINT,
  tenant_id VARCHAR,
  panel_current_utilization DOUBLE,
  tenant_kwh_usage BIGINT
) WITH (
  KAFKA_TOPIC = 'panel-readings',
  PARTITIONS = 6,
  KEY_FORMAT = 'JSON',
  VALUE_FORMAT = 'JSON'
);

-- Create a filtered Stream of panel readings registering power usage >= 85%
--  good for determining panels which are drawing a high electrical load
CREATE STREAM overloaded_panels AS 
  SELECT panel_id, tenant_id, panel_current_utilization 
    FROM panel_power_readings 
    WHERE panel_current_utilization >= 0.85
  EMIT CHANGES;

-- Create a stream of billable power events 
--  the tenant_kwh_usage field is the aggregate amount of power used in the
--  current month 
CREATE STREAM billable_power AS 
  SELECT 
      FORMAT_TIMESTAMP(FROM_UNIXTIME(panel_power_readings.ROWTIME), 'yyyy-MM') 
        AS billable_month,
      tenant_occupancy.customer_id as customer_id,
      tenant_occupancy.tenant_id as tenant_id, 
      panel_power_readings.tenant_kwh_usage as tenant_kwh_usage
    FROM panel_power_readings
    INNER JOIN tenant_occupancy ON 
      panel_power_readings.tenant_id = tenant_occupancy.tenant_id
  EMIT CHANGES;

-- Create a table that can be queried for billing reports
CREATE TABLE billable_power_report WITH (KEY_FORMAT = 'JSON') AS
  SELECT customer_id, tenant_id, billable_month, MAX(tenant_kwh_usage) as kwh
    FROM billable_power
    GROUP BY tenant_id, customer_id, billable_month;
