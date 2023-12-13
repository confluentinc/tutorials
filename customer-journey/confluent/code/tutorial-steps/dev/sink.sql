-- Send data to Elasticsearch
CREATE SINK CONNECTOR IF NOT EXISTS recipe_elasticsearch_customer_journey WITH (
  'connector.class'          = 'ElasticsearchSink',
  'input.data.format'        = 'JSON',
  'kafka.api.key'            = '<my-kafka-api-key>',
  'kafka.api.secret'         = '<my-kafka-api-secret>',
  'topics'                   = 'pages_per_customer',
  'connection.url'           = '<elasticsearch-URI>',
  'connection.user'          = '<elasticsearch-username>',
  'connection.password'      = '<elasticsearch-password>',
  'type.name'                = 'type.name=kafkaconnect',
  'key.ignore'               = 'true',
  'schema.ignore'            = 'true'
);
