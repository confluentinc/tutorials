CREATE SOURCE CONNECTOR IF NOT EXISTS iot_telemetry WITH (
  'connector.class'          = 'PostgresSource',
  'name'                     = 'recipe-postgres-iot_telemetry',
  'kafka.api.key'            = '<my-kafka-api-key>',
  'kafka.api.secret'         = '<my-kafka-api-secret>',
  'connection.host'          = '<database-host>',
  'connection.port'          = '5432',
  'connection.user'          = 'postgres',
  'connection.password'      = '<database-password>',
  'db.name'                  = '<db-name>',
  'table.whitelist'          = 'iot_telemetry',
  'timestamp.column.name'    = 'created_at',
  'output.data.format'       = 'JSON',
  'db.timezone'              = 'UTC',
  'tasks.max'                = '1'
);
