-- Stream of sales_orders
CREATE SOURCE CONNECTOR IF NOT EXISTS recipe_sqlservercdc_orders WITH (
  'connector.class'          = 'SqlServerCdcSource',
  'kafka.api.key'            = '<my-kafka-api-key>',
  'kafka.api.secret'         = '<my-kafka-api-secret>',
  'database.hostname'        = '<db-name>',
  'database.port'            = '1433',
  'database.user'            = '<database-username>',
  'database.password'        = '<database-password>',
  'database.dbname'          = 'database-name',
  'database.server.name'     = 'sql',
  'table.include.list'       = '<table_name>',
  'snapshot.mode'            = 'initial',
  'output.data.format'       = 'JSON',
  'tasks.max'                = '1'
);

-- Stream of customers
CREATE SOURCE CONNECTOR IF NOT EXISTS recipe_oracle_customers WITH (
  'connector.class'          = 'OracleDatabaseSource',
  'kafka.api.key'            = '<my-kafka-api-key>',
  'kafka.api.secret'         = '<my-kafka-api-secret>',
  'topic.prefix'             = 'oracle_',
  'connection.host'          = '<database-host>',
  'connection.port'          = '1521',
  'connection.user'          = '<database-username>',
  'connection.password'      = '<database-password>',
  'db.name'                  = '<db-name>',
  'table.whitelist'          = 'CUSTOMERS',
  'timestamp.column.name'    = 'created_at',
  'output.data.format'       = 'JSON',
  'db.timezone'              = 'UCT',
  'tasks.max'                = '1'
);
