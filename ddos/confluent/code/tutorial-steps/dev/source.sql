-- Example
CREATE SOURCE CONNECTOR IF NOT EXISTS network_traffic_source WITH (
  'connector.class'   = 'RabbitMQSource',
  'name'              = 'network_traffic_source',
  'kafka.api.key'     = '<my-kafka-api-key>',
  'kafka.api.secret'  = '<my-kafka-api-secret>',
  'kafka.topic'       = 'network-traffic',
  'rabbitmq.host'     = '<my-rabbitmq-host>',
  'rabbitmq.username' = '<my-rabbitmq-username>',
  'rabbitmq.password' = '<my-rabbitmq-password>',
  'rabbitmq.queue'    = '<my-rabbitmq-queue>',
  'tasks.max'         = '1'
);
