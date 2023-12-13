-- Stream of fish weight predictions
CREATE SOURCE CONNECTOR IF NOT EXISTS recipe_mongodb_predictions WITH (
  'connector.class'        = 'MongoDbAtlasSource',
  'kafka.api.key'          = '<my-kafka-api-key>',
  'kafka.api.secret'       = '<my-kafka-api-secret>',
  'connection.host'        = '<database-host-address>',
  'connection.user'        = '<database-username>',
  'connection.password'    = '<database-password>',
  'topic.prefix'           = 'kt',
  'database'               = 'mdb',
  'collection'             = 'weight-prediction',
  'poll.await.time.ms'     = '5000',
  'poll.max.batch.size'    = '1000',
  'copy.existing'          = 'true',
  'output.data.format'     = 'JSON',
  'tasks.max'              = '1'
);

-- Stream of actual fish weights
CREATE SOURCE CONNECTOR IF NOT EXISTS recipe_mongodb_actual_weights WITH (
  'connector.class'        = 'MongoDbAtlasSource',
  'kafka.api.key'          = '<my-kafka-api-key>',
  'kafka.api.secret'       = '<my-kafka-api-secret>',
  'connection.host'        = '<database-host-address>',
  'connection.user'        = '<database-username>',
  'connection.password'    = '<database-password>',
  'topic.prefix'           = 'kt',
  'database'               = 'mdb',
  'collection'             = 'machine-weight',
  'poll.await.time.ms'     = '5000',
  'poll.max.batch.size'    = '1000',
  'copy.existing'          = 'true',
  'output.data.format'     = 'JSON',
  'tasks.max'              = '1'
);

