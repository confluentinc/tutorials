from asyncio import get_event_loop
import json
import logging
from typing import NamedTuple

from aiokafka.helpers import create_ssl_context
from decouple import config, AutoConfig, RepositoryEnv, Csv
from faust import App, SASLCredentials
from schema_registry.client import SchemaRegistryClient

from schema_registry.client.utils import AVRO_SCHEMA_TYPE, JSON_SCHEMA_TYPE
from schema_registry.serializers import AvroMessageSerializer, JsonMessageSerializer
from schema_registry.serializers.errors import SerializerError

from pii.analyzer import PresidioAnalyzer, anonymizer
from pii.language_detection import LanguageDetector
from pii.record_processing import AsyncAvroProcessor, AsyncJsonProcessor
from pii.schema_explorer import SchemaExplorer, is_json
from pii.schema_registry_serder import decode_schema_id
from pii.settings_utils import list_or_string_to_list
from pii.spacy_model import init_models
from pii.stream_catalog import StreamCatalogTagQuery, StreamCatalogError


class Auth(NamedTuple):
    username: str
    password: str


env_file = config('PII_ENV_FILE', default='')
if env_file:
    AutoConfig.SUPPORTED = {env_file: RepositoryEnv}
    config = AutoConfig()

auth_env_var = 'USE_AUTH'
use_authorisation = config(auth_env_var, default=True, cast=bool)

faust_creds = SASLCredentials(username=config('CLUSTER_API_KEY'), password=config('CLUSTER_API_SECRET'),
                              ssl_context=create_ssl_context()) if use_authorisation else None
num_topic_partitions = config('TOPIC_PARTITIONS', default=1, cast=int)

offset = config('OFFSET', default='earliest')
offset_reset_policy = offset if not offset.isdigit() else None

app = App('pii-detector',
          broker=config('KAFKA_BROKER_URL'),
          broker_credentials=faust_creds,
          topic_replication_factor=config('TOPIC_REPLICATION_FACTOR'),
          topic_partitions=num_topic_partitions,
          consumer_auto_offset_reset=offset_reset_policy)

sr_auth = Auth(config('SR_API_KEY'), config('SR_API_SECRET')) if use_authorisation else None
sr_url_env_var = 'SCHEMA_REGISTRY_URL'
sr_url = config(sr_url_env_var)
if use_authorisation:
    assert sr_url.startswith('https://'), \
        f'Authorisation requires use of https protocol. Set {auth_env_var} to False or change {sr_url_env_var}'
sr_client = SchemaRegistryClient(url=sr_url, auth=sr_auth)

field_tags = config('IGNORE_FIELD_TAGS', cast=Csv(), default='')
catalog_tag_query = StreamCatalogTagQuery(sr_client.url_manager.base_url, sr_auth, field_tags) if field_tags else None

in_topic = app.topic(config('IN_TOPIC'), internal=True, value_serializer='raw', key_serializer='raw')
anon_topic = app.topic(config('OUT_TOPIC'))
entity_alert_topic = app.topic(config('ALERT_TOPIC'))

# init models + analyzer

pii_detection_config = init_models(config("MODELS"))
analyzer = PresidioAnalyzer(pii_detection_config,
                            entity_types=config('ENTITY_TYPES', cast=Csv(), default=''),
                            extend_default_entity_types=config('EXTEND_DEFAULT_ENTITIES', default=True),
                            custom_recognizers_yaml_path=config('CUSTOM_RECOGNIZER_YAML', default=None))

# Language detection init (warning: setting it to "auto" costs 800 MB in memory)
# empty or "auto" = default --> all supported languages are pre-loaded and lang is detected on each string
# or a list of language codes separated by commas
# if only language code is provided, no language detection happens
lang_settings = config("LANGUAGES", "auto")
if lang_settings == "auto":
    lang_codes = []
else:
    lang_codes = list_or_string_to_list(lang_settings) if lang_settings else []

if len(lang_codes) == 1:
    lang_code = lang_codes[0]
    lang_detector = None
else:
    lang_code = None
    lang_detector = LanguageDetector(lang_codes)


class PartitionOffsetter:
    def __init__(self, index: int, offset: int):
        self._index = index
        self._offset = offset

    async def set_offset(self):
        from faust.types import TP
        await app.consumer.seek(TP(in_topic.get_topic_name(), self._index), self._offset)
        logging.info(f'Moved partition {self._index} offset to: {self._offset}')


if offset.isdigit():
    offset = int(offset)
    for partition in range(num_topic_partitions):
        app.task()(PartitionOffsetter(partition, offset).set_offset)


async def anonymize(raw_text: str) -> str:
    language = lang_code if lang_code else lang_detector.detect_lang(raw_text)

    analysis_results = analyzer.analyze(raw_text, language=language)
    print(f'Analysis results: {analysis_results}')
    anonymized_result = anonymizer.anonymize(text=raw_text, analyzer_results=analysis_results)
    print(f'Anonymised Text: {anonymized_result.text}')

    for (detected_entity, anonymized_entity) in zip(analysis_results, anonymized_result.items):
        alert = dict(
            entity_type=anonymized_entity.entity_type,
            operation=str(anonymized_entity),
            text=raw_text,
            confidence_score=detected_entity.score,
            language=language,
            topic=in_topic.get_topic_name(),


        )
        await entity_alert_topic.send(value=alert)
    return anonymized_result.text


async def anonymize_record(record):
    schema_explorer = SchemaExplorer(record)

    for field_name, field_value in schema_explorer.all_text_fields():
        anonymized_value = anonymize(field_value)
        res = schema_explorer.update_field_from_dotted(field_name, anonymized_value)
        if not res:
            logging.error(f"{field_name} was not found in the record. The field will not be anonymized.")
    return record


@app.agent(in_topic, sink=[anon_topic])
async def pii_detection(stream):
    async for message_bytes in stream:
        try:
            in_schema_id = decode_schema_id(message_bytes)
        except SerializerError:
            in_schema_id = None

        if in_schema_id:
            tagged_field_names = {}
            if catalog_tag_query:
                try:
                    tagged_field_names = catalog_tag_query(in_schema_id)
                except StreamCatalogError:
                    logging.exception(f'Contacting Stream Catalog failed, cannot retrieve fields with tags {field_tags}'
                                      'Defaulting to no ignored fields')
            out_message_subject = f'{anon_topic.get_topic_name()}-value'
            in_schema = sr_client.get_by_id(in_schema_id)
            if in_schema.schema_type == AVRO_SCHEMA_TYPE:
                serdes = AvroMessageSerializer(sr_client)
                format_anonymizer = AsyncAvroProcessor(anonymize)
            elif in_schema.schema_type == JSON_SCHEMA_TYPE:
                serdes = JsonMessageSerializer(sr_client)
                format_anonymizer = AsyncJsonProcessor(anonymize)
            else:
                raise ValueError(f'Schema type {in_schema.schema_type} is not supported')
            in_message = serdes.decode_message(message_bytes)
            out_message = await format_anonymizer.process_record(in_schema.schema, in_message, tagged_field_names)
            encoded_out_message = serdes.encode_record_with_schema(out_message_subject, in_schema, out_message)
        else:
            msg_str = message_bytes.decode("utf-8")
            if is_json(msg_str):
                json_obj = json.loads(msg_str)
                encoded_out_message = await anonymize_record(json_obj)
            else:
                encoded_out_message = await anonymize(msg_str)
        yield encoded_out_message


def main():
    event_loop = get_event_loop()
    for topic in (in_topic, anon_topic, entity_alert_topic):
        event_loop.run_until_complete(topic.maybe_declare())
    app.main()


if __name__ == '__main__':
    main()
