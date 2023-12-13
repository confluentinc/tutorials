CREATE SOURCE CONNECTOR IF NOT EXISTS FLEET_SENSORS WITH (
    'connector.class'                             = 'io.confluent.kafka.connect.datagen.DatagenConnector',
    'quickstart'                                  = 'fleet_mgmt_sensors',
    'schema.keyfield'                             = 'vehicle_id',
    'kafka.topic'                                 = 'fleet_mgmt_sensors',
    'key.converter'                               = 'org.apache.kafka.connect.converters.IntegerConverter',
    'value.converter'                             = 'io.confluent.connect.avro.AvroConverter',
    'value.converter.schema.registry.url'         = 'http://schema-registry:8081',
    'value.converter.schemas.enable'              = 'false',
    'max.interval'                                = 500,
    'tasks.max'                                   = '1'
);
