CREATE STREAM clicks (ip VARCHAR, url VARCHAR, timestamp VARCHAR)
WITH (KAFKA_TOPIC='clicks',
      TIMESTAMP='timestamp',
      TIMESTAMP_FORMAT='yyyy-MM-dd''T''HH:mm:ssX',
      PARTITIONS=1,
      VALUE_FORMAT='AVRO');

INSERT INTO clicks (ip, timestamp, url) VALUES ('51.56.119.117',FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP()),'yyyy-MM-dd''T''HH:mm:ssX'),'/etiam/justo/etiam/pretium/iaculis.xml');
INSERT INTO clicks (ip, timestamp, url) VALUES ('51.56.119.117',FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() + (60 * 1000)),'yyyy-MM-dd''T''HH:mm:ssX'),'/nullam/orci/pede/venenatis.json');
INSERT INTO clicks (ip, timestamp, url) VALUES ('53.170.33.192',FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() + (91 * 1000)),'yyyy-MM-dd''T''HH:mm:ssX'),'/mauris/morbi/non.jpg');
INSERT INTO clicks (ip, timestamp, url) VALUES ('51.56.119.117',FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() + (96 * 1000)),'yyyy-MM-dd''T''HH:mm:ssX'),'/convallis/nunc/proin.jsp');
INSERT INTO clicks (ip, timestamp, url) VALUES ('53.170.33.192',FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() + (2 * 60 * 1000)),'yyyy-MM-dd''T''HH:mm:ssX'),'/vestibulum/vestibulum/ante/ipsum/primis/in.json');
INSERT INTO clicks (ip, timestamp, url) VALUES ('51.56.119.117',FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() + (63 * 60 * 1000) + 21),'yyyy-MM-dd''T''HH:mm:ssX'),'/vehicula/consequat/morbi/a/ipsum/integer/a.jpg');
INSERT INTO clicks (ip, timestamp, url) VALUES ('51.56.119.117',FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() + (63 * 60 * 1000) + 50),'yyyy-MM-dd''T''HH:mm:ssX'),'/pede/venenatis.jsp');
INSERT INTO clicks (ip, timestamp, url) VALUES ('53.170.33.192',FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() + (100 * 60 * 1000)),'yyyy-MM-dd''T''HH:mm:ssX'),'/nec/euismod/scelerisque/quam.xml');
INSERT INTO clicks (ip, timestamp, url) VALUES ('53.170.33.192',FORMAT_TIMESTAMP(FROM_UNIXTIME(UNIX_TIMESTAMP() + (100 * 60 * 1000) + 9),'yyyy-MM-dd''T''HH:mm:ssX'),'/ligula/nec/sem/duis.jsp');

SELECT ip,
       FORMAT_TIMESTAMP(FROM_UNIXTIME(WINDOWSTART),'yyyy-MM-dd HH:mm:ss', 'UTC') AS session_start_ts,
       FORMAT_TIMESTAMP(FROM_UNIXTIME(WINDOWEND),'yyyy-MM-dd HH:mm:ss', 'UTC')   AS session_end_ts,
       COUNT(*) AS click_count,
       WINDOWEND - WINDOWSTART AS session_length_ms
FROM CLICKS
WINDOW SESSION (5 MINUTES)
GROUP BY ip
EMIT CHANGES;
