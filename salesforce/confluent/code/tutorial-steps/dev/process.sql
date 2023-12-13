SET 'auto.offset.reset' = 'earliest';

-- Register the stream of SFDC CDC Opportunities
CREATE STREAM stream_sfdc_cdc_opportunity_raw (
  replayid INT,
  changeeventheader STRUCT<
    changetype VARCHAR>
) WITH (
  KAFKA_TOPIC = 'sfdc.cdc.raw',
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 6
);

-- Create a new stream with Replay ID and Change Event Header for just Gap Events
CREATE STREAM stream_sfdc_cdc_opportunity_change_log AS
  SELECT
    replayid,
    changeeventheader
  FROM stream_sfdc_cdc_opportunity_raw
  WHERE UCASE(changeeventheader->changetype) LIKE 'GAP%'
  EMIT CHANGES;
