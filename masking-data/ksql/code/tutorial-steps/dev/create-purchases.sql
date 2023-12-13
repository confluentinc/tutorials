CREATE STREAM purchases (order_id INT, customer_name VARCHAR, date_of_birth VARCHAR,
                         product VARCHAR, order_total_usd DOUBLE, town VARCHAR, country VARCHAR)
    WITH (kafka_topic='purchases', value_format='json', partitions=1);
