import click
from confluent_kafka import KafkaException
from confluent_kafka.aio import AIOProducer
from confluent_kafka.schema_registry import AsyncSchemaRegistryClient
from confluent_kafka.schema_registry._async.avro import AsyncAvroSerializer
from confluent_kafka.serialization import SerializationContext, MessageField
from jproperties import Properties
from quart import Quart, jsonify


kafka_properties_file = None
sr_properties_file = None
producer = None
avro_serializer = None

def create_app():
    app = Quart(__name__)
    app = cli_wrapper(app)
    return app

def cli_wrapper(app):
    @app.cli.command("serve")
    @click.argument("kafka_properties_file_arg")
    @click.argument("sr_properties_file_arg")
    def serve_command(kafka_properties_file_arg, sr_properties_file_arg):
        global kafka_properties_file
        global sr_properties_file
        kafka_properties_file = kafka_properties_file_arg
        sr_properties_file = sr_properties_file_arg

        app.run()
    return app

app = create_app()

@app.before_serving
async def create_producer():
    global producer
    print(f"Running server with Kafka properties file {kafka_properties_file}")
    producer_conf = Properties()
    with open(kafka_properties_file, "rb") as f:
        producer_conf.load(f, "utf-8")
    producer = AIOProducer(producer_conf.properties)

@app.before_serving
async def create_avro_serializer():
    global avro_serializer
    schema_registry_conf = Properties()
    with open(sr_properties_file, "rb") as f:
        schema_registry_conf.load(f, "utf-8")

    schema_registry_client = AsyncSchemaRegistryClient(schema_registry_conf.properties)

    # Example Avro schema
    schema_str = u'{"type": "record", "name": "Reading", "fields": [{"name": "temp", "type": "float"}]}'

    # Instantiate async serializer
    avro_serializer = await AsyncAvroSerializer(schema_registry_client, schema_str=schema_str)

@app.after_serving
async def close_producer():
    if producer is not None:
        # flush any remaining buffered messages before shutdown
        await producer.flush()
        await producer.close()

@app.post("/record_temp/<temp>")
async def record_temp(temp):
    try:
        delivery_future = await producer.produce("readings", value=temp)
        delivered_msg = await delivery_future
        if delivered_msg.error() is not None:
            return jsonify({"status": "error", "message": f"{delivered_msg.error().str()}"}), 500
        else:
            return jsonify({"status": "success"}), 200
    except KafkaException as ke:
        return jsonify({"status": "error", "message": f"{ke.args[0].str()}"}), 500
    except Exception as e:
        return jsonify({"status": "error", "message": f"{e.message}"}), 500

@app.post("/record_temp_schematized/<temp>")
async def record_temp_schematized(temp):
    try:
        value = {"temp": float(temp)}
        serialized_value = await avro_serializer(value,
                                                 SerializationContext("readings_schematized",
                                                                      MessageField.VALUE))
        delivery_future = await producer.produce("readings_schematized", value=serialized_value)
        delivered_msg = await delivery_future
        print(f"Produced to {delivered_msg.topic()} [{delivered_msg.partition()}] @ {delivered_msg.offset()}")
        if delivered_msg.error() is not None:
            return jsonify({"status": "error", "message": f"{delivered_msg.error().str()}"}), 500
        else:
            return jsonify({"status": "success"}), 200
    except KafkaException as ke:
        return jsonify({"status": "error", "message": f"{ke.args[0].str()}"}), 500
    except Exception as e:
        return jsonify({"status": "error", "message": f"{e.message}"}), 500
