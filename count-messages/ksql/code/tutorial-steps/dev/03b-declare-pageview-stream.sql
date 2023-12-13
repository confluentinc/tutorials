CREATE STREAM pageviews (msg VARCHAR) 
  WITH (KAFKA_TOPIC ='pageviews', 
        VALUE_FORMAT='JSON');