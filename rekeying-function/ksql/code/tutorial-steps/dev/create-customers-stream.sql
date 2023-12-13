CREATE STREAM customers (id int key, firstname string, lastname string, phonenumber string)
  WITH (kafka_topic='customers',
        partitions=2,        
        value_format = 'avro');
