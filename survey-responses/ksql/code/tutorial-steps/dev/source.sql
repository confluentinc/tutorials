CREATE SOURCE CONNECTOR IF NOT EXISTS recipe_servicenow_survey_analysis_respondents WITH (
  'connector.class'    = 'ServiceNowSource',
  'kafka.auth.mode'    = 'KAFKA_API_KEY',
  'kafka.api.key'      = '<my-kafka-api-key>',
  'kafka.api.secret'   = '<my-kafka-api-secret>',
  'kafka.topic'        = 'survey-respondents',
  'output.data.format' = 'JSON',
  'servicenow.url'     = 'my.servicenow.instance',
  'servicenow.table'   = 'respondents',
  'servicenow.user'    = 'servicenowuser',
  'servicenow.password'= '********',
  'tasks.max'          = '1'
);

CREATE SOURCE CONNECTOR IF NOT EXISTS recipe_servicenow_survey_analysis_responses WITH (
  'connector.class'    = 'ServiceNowSource',
  'kafka.auth.mode'    = 'KAFKA_API_KEY',
  'kafka.api.key'      = '<my-kafka-api-key>',
  'kafka.api.secret'   = '<my-kafka-api-secret>',
  'kafka.topic'        = 'survey-responses',
  'output.data.format' = 'JSON',
  'servicenow.url'     = 'my.servicenow.instance',
  'servicenow.table'   = 'surveys-responses',
  'servicenow.user'    = 'servicenowuser',
  'servicenow.password'= '********',
  'tasks.max'          = '1'
);
