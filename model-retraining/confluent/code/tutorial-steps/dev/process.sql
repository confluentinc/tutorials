SET 'auto.offset.reset' = 'earliest';

-- Create stream of predictions
CREATE STREAM predicted_weight(
  fish_id VARCHAR KEY,
  species VARCHAR,
  height DOUBLE,
  length DOUBLE,
  prediction DOUBLE
) WITH (
  KAFKA_TOPIC = 'kt.mdb.weight-prediction', 
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 6
);

-- Create stream of actual weights
CREATE STREAM actual_weight(
  fish_id VARCHAR KEY,
  species VARCHAR,
  weight DOUBLE
) WITH (
  KAFKA_TOPIC = 'kt.mdb.machine-weight', 
  VALUE_FORMAT = 'JSON',
  PARTITIONS = 6
);

-- Create stream joining predictions with actual weights
CREATE STREAM diff_weight WITH (KAFKA_TOPIC = 'diff_weight') AS
  SELECT
   -- This fake key field will give us something to group by in the next step
   'key' AS key, 
   predicted_weight.fish_id AS fish_id,
   predicted_weight.species AS species,
   predicted_weight.length AS length,
   predicted_weight.height AS height,
   predicted_weight.prediction AS prediction,
   actual_weight.weight AS actual,
   ROUND(ABS(predicted_weight.prediction - actual_weight.weight) / actual_weight.weight, 3) AS Error
FROM predicted_weight
INNER JOIN actual_weight
WITHIN 1 MINUTE
GRACE PERIOD 1 MINUTE
ON predicted_weight.fish_id = actual_weight.fish_id;

-- Create table of one minute aggregates with over 15% error rate
CREATE TABLE retrain_weight WITH (KAFKA_TOPIC = 'retrain_weight') AS
 SELECT
   key,
   COLLECT_SET(species) AS species,
   EARLIEST_BY_OFFSET(fish_id) AS fish_id_start,
   LATEST_BY_OFFSET(fish_id) AS fish_id_end,
   AVG(Error) AS ErrorAvg
FROM diff_weight
WINDOW TUMBLING (SIZE 1 MINUTE, GRACE PERIOD 1 MINUTE)
GROUP BY key
HAVING ROUND(AVG(diff_weight.Error), 2) > 0.15;
