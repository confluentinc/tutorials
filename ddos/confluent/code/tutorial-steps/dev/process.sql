SET 'auto.offset.reset' = 'earliest';

CREATE STREAM network_traffic (
  timestamp BIGINT,
  layers STRUCT<
   frame STRUCT< 
      time VARCHAR, 
      protocols VARCHAR >,
   eth STRUCT< 
      src VARCHAR, 
      dst VARCHAR >,
   ip STRUCT< 
      src VARCHAR, 
      src_host VARCHAR, 
      dst VARCHAR, 
      dst_host VARCHAR, 
      proto VARCHAR >,
   tcp STRUCT< 
      srcport VARCHAR, 
      dstport VARCHAR, 
      flags_ack VARCHAR, 
      flags_reset VARCHAR >>
) WITH (
  KAFKA_TOPIC = 'network-traffic', 
  TIMESTAMP = 'timestamp', 
  VALUE_FORMAT = 'JSON', 
  PARTITIONS = 6
);


CREATE TABLE potential_slowloris_attacks AS 
SELECT 
  layers->ip->src, count(*) as count_connection_reset
FROM network_traffic 
  WINDOW TUMBLING (SIZE 60 SECONDS)
WHERE layers->tcp->flags_ack = '1' AND layers->tcp->flags_reset = '1'
GROUP BY
  layers->ip->src
HAVING 
  count(*) > 10;
