SET 'auto.offset.reset' = 'earliest';

-- Extract relevant fields from log messages
CREATE STREAM syslog (
  ts BIGINT,
  host VARCHAR,
  facility INT,
  message VARCHAR,
  remote_address VARCHAR
) WITH (
  KAFKA_TOPIC = 'syslog',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 6,
  TIMESTAMP='ts'
);

-- Create actionable stream of SSH attacks, filtering syslog messages where user is invalid,
-- and enriched with user and IP
CREATE STREAM ssh_attacks AS
  SELECT
    FORMAT_TIMESTAMP(FROM_UNIXTIME(ts), 'yyyy-MM-dd HH:mm:ss') AS syslog_timestamp,
    host,
    facility,
    CASE WHEN facility = 0 THEN 'kernel messages'
         WHEN facility = 1 THEN 'user-level messages'
         WHEN facility = 2 THEN 'mail system'
         WHEN facility = 3 THEN 'system daemons'
         WHEN facility = 4 THEN 'security/authorization messages'
         WHEN facility = 5 THEN 'messages generated internally by syslogd'
         WHEN facility = 6 THEN 'line printer subsystem'
         ELSE '<unknown>'
       END AS facility_description,
    SPLIT(REPLACE(message, 'Invalid user ', ''), ' from ')[1] AS attack_user,
    remote_address AS attack_ip
  FROM syslog
  WHERE message LIKE 'Invalid user%'
  EMIT CHANGES;
