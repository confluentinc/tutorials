CREATE SINK CONNECTOR IF NOT EXISTS JDBC_SINK_POSTGRES_01 WITH (
    'connector.class'     = 'io.confluent.connect.jdbc.JdbcSinkConnector',
    'connection.url'      = 'jdbc:postgresql://postgres:5432/',
    'connection.user'     = 'postgres',
    'connection.password' = 'postgres',
    'topics'              = 'TEMPERATURE_READINGS_TIMESTAMP_MT',
    'auto.create'         = 'true',
    'auto.evolve'         = 'true'
  );
