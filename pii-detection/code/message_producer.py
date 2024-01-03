import random
from asyncio import get_event_loop
from dataclasses import dataclass
from typing import NamedTuple

from aiokafka.helpers import create_ssl_context
from dataclasses_avroschema import AvroModel
from decouple import config, AutoConfig, RepositoryEnv
from faust import App, SASLCredentials
from presidio_evaluator.data_generator import PresidioSentenceFaker
from schema_registry.client import SchemaRegistryClient
from schema_registry.client.schema import AvroSchema
from schema_registry.serializers import AvroMessageSerializer


class Auth(NamedTuple):
    username: str
    password: str


@dataclass
class RawData(AvroModel):
    event_id: int
    log: str


faker = PresidioSentenceFaker(locale='en', lower_case_ratio=0.05)
fake_records = faker.generate_new_fake_sentences(num_samples=1000)

env_file = config('PII_ENV_FILE', default='')
if env_file:
    AutoConfig.SUPPORTED = {env_file: RepositoryEnv}
    config = AutoConfig()

auth_env_var = 'USE_AUTH'
use_authorisation = config(auth_env_var, default=True, cast=bool)

faust_creds = SASLCredentials(username=config('CLUSTER_API_KEY'), password=config('CLUSTER_API_SECRET'),
                              ssl_context=create_ssl_context()) if use_authorisation else None
app = App('message-producer',
          broker=config('KAFKA_BROKER_URL'),
          broker_credentials=faust_creds,
          topic_replication_factor=config('TOPIC_REPLICATION_FACTOR'),
          topic_partitions=1)

sr_auth = Auth(config('SR_API_KEY'), config('SR_API_SECRET')) if use_authorisation else None
sr_client = SchemaRegistryClient(url=config('SCHEMA_REGISTRY_URL'), auth=sr_auth)
avro_serdes = AvroMessageSerializer(sr_client)

topic_name = config('IN_TOPIC')
raw_data_topic = app.topic(topic_name, internal=True)
event_loop = get_event_loop()
event_loop.run_until_complete(raw_data_topic.maybe_declare())


@app.timer(1.0)
async def produce_raw_data():
    fake_record = random.choice(fake_records)
    snippet = fake_record.fake
    data = RawData(random.randint(0, 1000), snippet).asdict()
    schema = AvroSchema(RawData.avro_schema())
    data = avro_serdes.encode_record_with_schema(f'{topic_name}-value', schema, data)
    await raw_data_topic.send(value=data)


if __name__ == '__main__':
    import sys

    sys.argv = [sys.argv[0], 'worker', '-l', 'info', '--web-port', '7001']
    app.main()
