CREATE SINK CONNECTOR IF NOT EXISTS recipe_mongodb_training_weights WITH (
    'connector.class'          = 'MongoDbAtlasSink',
    'kafka.auth.mode'          = 'KAFKA_API_KEY',
    'kafka.api.key'            = '<my-kafka-api-key',
    'kafka.api.secret'         = '<my-kafka-api-secret>',
    'input.data.format'        = 'JSON',
    'connection.host'          = '<database-host-address>',
    'connection.user'          = '<my-username>',
    'connection.password'      = '<my-password>',
    'topics'                   = 'diff_weight',
    'max.num.retries'          = '3',
    'retries.defer.timeout'    = '5000',
    'max.batch.size'           = '0',
    'database'                 = 'mdb',
    'collection'               = 'training_data',
    'tasks.max'                = '1'
);     

CREATE SINK CONNECTOR IF NOT EXISTS recipe_http_retrain_trigger WITH (
    'connector.class'          = 'HttpSink',
    'input.data.format'        = 'JSON',
    'kafka.auth.mode'          = 'KAFKA_API_KEY',
    'kafka.api.key'            = '<my-kafka-api-key>',
    'kafka.api.secret'         = '<my-kafka-api-secret>',
    'topics'                   = 'retrain_weight',
    'tasks.max'                = '1',
    'http.api.url'             = '<training-endpoint-url>',
    'request.method'           = 'POST'
);
