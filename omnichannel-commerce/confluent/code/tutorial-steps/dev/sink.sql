-- Send data to Elasticsearch
CREATE SINK CONNECTOR IF NOT EXISTS analyzed_clickstream WITH (
  'connector.class'          = 'ElasticsearchSink',
  'name'                     = 'recipe-elasticsearch-analyzed_clickstream',
  'input.data.format'        = 'JSON',
  'kafka.api.key'            = '<my-kafka-api-key>',
  'kafka.api.secret'         = '<my-kafka-api-secret>',
  'topics'                   = 'orders_enriched',
  'connection.url'           = '<elasticsearch-URI>',
  'connection.user'          = '<elasticsearch-username>',
  'connection.password'      = '<elasticsearch-password>',
  'type.name'                = 'type.name=kafkaconnect',
  'key.ignore'               = 'true',
  'schema.ignore'            = 'true'
);
