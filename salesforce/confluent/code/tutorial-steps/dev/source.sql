-- Stream of changes to Salesforce records
CREATE SOURCE CONNECTOR IF NOT EXISTS recipe_salesforcecdc_account_changes WITH (
  'connector.class'            = 'SalesforceCdcSource',
  'kafka.api.key'              = '<my-kafka-api-key>',
  'kafka.api.secret'           = '<my-kafka-api-secret>',
  'kafka.topic'                = 'sfdc.cdc.raw',
  'salesforce.username'        = '<my-sfdc-username>',
  'salesforce.password'        = '<my-sfdc-password>',
  'salesforce.password.token'  = '<sfdc-password-token>',
  'salesforce.consumer.key'    = '<sfdc-consumer-key>',
  'salesforce.consumer.secret' = '<sfdc-consumer-secret>',
  'salesforce.cdc.name'        = 'AccountChangeEvent',
  'output.data.format'         = 'JSON',
  'tasks.max'                  = '1'
);
