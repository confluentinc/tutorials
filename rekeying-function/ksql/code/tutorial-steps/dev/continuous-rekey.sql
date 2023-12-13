CREATE STREAM customers_by_area_code
  WITH (KAFKA_TOPIC='customers_by_area_code') AS
    SELECT
      REGEXREPLACE(phonenumber, '\\(?(\\d{3}).*', '$1') AS AREA_CODE,
      id,
      firstname,
      lastname,
      phonenumber
    FROM customers
    PARTITION BY REGEXREPLACE(phonenumber, '\\(?(\\d{3}).*', '$1')
    EMIT CHANGES;
