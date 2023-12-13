-- Stream of fleet descriptions
CREATE SOURCE CONNECTOR IF NOT EXISTS recipe_mongodb_fleet_description WITH (
  'connector.class'          = 'MongoDbAtlasSource',
  'kafka.api.key'            = '<my-kafka-api-key>',
  'kafka.api.secret'         = '<my-kafka-api-secret>',
  'connection.host'          = '<database-host-address>',
  'connection.user'          = '<database-username>',
  'connection.password'      = '<database-password>',
  'database'                 = '<database-name>',
  'collection'               = '<database-collection-name>',
  'poll.await.time.ms'       = '5000',
  'poll.max.batch.size'      = '1000',
  'copy.existing'            = 'true',
  'output.data.format'       = 'JSON',
  'tasks.max'                = '1'
);

-- Stream of current location of each vehicle in the fleet
CREATE SOURCE CONNECTOR IF NOT EXISTS recipe_postgres_fleet_location WITH (
  'connector.class'          = 'PostgresSource',
  'kafka.api.key'            = '<my-kafka-api-key>',
  'kafka.api.secret'         = '<my-kafka-api-secret>',
  'connection.host'          = '<database-host>',
  'connection.port'          = '5432',
  'connection.user'          = 'postgres',
  'connection.password'      = '<database-password>',
  'db.name'                  = '<db-name>',
  'table.whitelist'          = 'fleet_location',
  'timestamp.column.name'    = 'created_at',
  'output.data.format'       = 'JSON',
  'db.timezone'              = 'UTC',
  'tasks.max'                = '1'
);
