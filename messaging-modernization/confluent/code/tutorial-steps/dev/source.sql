CREATE SOURCE CONNECTOR IF NOT EXISTS recipe_rabbitmq_transactions WITH (
  'connector.class'          = 'RabbitMQSource',
  'kafka.api.key'            = '<my-kafka-api-key>',
  'kafka.api.secret'         = '<my-kafka-api-secret>',
  'kafka.topic'              = 'from-rabbit',
  'rabbitmq.host'            = '<host>',
  'rabbitmq.username'        = '<username>',
  'rabbitmq.password'        = '<password>',
  'rabbitmq.queue'           = 'transactions',
  'tasks.max'                = '1'
);
