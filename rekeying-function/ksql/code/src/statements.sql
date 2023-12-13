CREATE STREAM customers (id int key, firstname string, lastname string, phonenumber string)
  WITH (kafka_topic='customers',
        partitions=2,
        value_format = 'avro');

CREATE STREAM customers_by_area_code
  WITH (KAFKA_TOPIC='customers_by_area_code') AS
    SELECT * FROM customers
    PARTITION BY REGEXREPLACE(phonenumber, '\\(?(\\d{3}).*', '$1')
    EMIT CHANGES;
