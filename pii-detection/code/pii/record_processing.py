from abc import ABC as ABSTRACT_BASE_CLASS, abstractmethod
from copy import copy, deepcopy
from typing import Set, Awaitable, Union, Any


class AsyncRecordProcessor(ABSTRACT_BASE_CLASS):
    def __init__(self, process_fn: Awaitable):
        self._process_fn = process_fn

    @abstractmethod
    async def process_record(self, schema: dict, record: dict, fields_to_ignore: Set[str] = {}) -> dict:
        raise NotImplementedError


class AsyncAvroProcessor(AsyncRecordProcessor):
    async def process_record(self, schema: dict, record: dict, fields_to_ignore: Set[str] = {}) -> dict:
        processed_record = copy(record)
        for field_schema in schema['fields']:
            field_name = field_schema['name']
            if field_name in fields_to_ignore:
                continue
            processed_record[field_name] = await self.process_field(field_schema, record[field_name], fields_to_ignore)
        return processed_record

    async def process_field(self, field_schema: dict, field_value: Any, *args) -> Any:
        if isinstance(field_value, str):
            return await self._process_fn(field_value)
        if field_schema['type'] in ('array', 'map', 'record'):
            return await self.process_complex_type(field_schema['name'], field_schema, field_value, *args)
        if isinstance(field_schema['type'], dict):
            return await self.process_complex_type(field_schema['name'], field_schema['type'], field_value, *args)
        return field_value

    async def process_complex_type(self, field_name: str, type_schema: dict, field_value: Any, *args) -> Any:
        field_type = type_schema['type']
        if field_type == 'record':
            return await self.process_record(type_schema, field_value)

        if field_type == 'map':
            item_schema = type_schema['values']
            if isinstance(item_schema, dict):
                item_schema['name'] = field_name
            else:
                item_schema = dict(name=field_name, type=item_schema)
            for key in field_value:
                field_value[key] = await self.process_field(item_schema, field_value[key], *args)
            return field_value

        if field_type == 'array':
            item_schema = type_schema['items']
        else:
            raise NotImplementedError

        if item_schema == 'string':
            return [await self._process_fn(value) for value in field_value]
        if isinstance(item_schema, list):
            return [await self._process_fn(value) if isinstance(value, str) else value for value in field_value]
        if isinstance(item_schema, dict):
            item_schema['name'] = field_name
            field_values = [await self.process_field(item_schema, value, *args) for value in field_value]
            return field_values
        return field_value


def _maybe_to_number(value: str) -> Union[int, float]:
    try:
        return int(value)
    except ValueError:
        try:
            return float(value)
        except ValueError:
            return value


class AsyncJsonProcessor(AsyncRecordProcessor):
    _complex_types = ('array', 'object',)
    _ref_key = '$ref'

    async def process_record(self, schema: dict, record: dict, fields_to_ignore: Set[str] = {}) -> dict:
        processed_record = deepcopy(record)
        ref_schemas = schema.get('definitions', None)
        for field_name, field_schema in schema['properties'].items():
            if field_name in fields_to_ignore:
                continue
            processed_record[field_name] = await self.process_field(field_name, field_schema, record[field_name],
                                                                    ref_schemas, fields_to_ignore)
        return processed_record

    async def process_field(self, field_name: str, field_schema: dict, field_value: Any,
                            ref_schemas: dict, *args) -> Any:
        if 'type' in field_schema:
            if field_schema['type'] == 'string':
                return await self._process_fn(field_value)
            if field_schema['type'] in self._complex_types:
                return await self.process_complex_type(field_name, field_schema, field_value, ref_schemas, *args)
        elif 'anyOf' in field_schema and dict(type='string') in field_schema['anyOf']:
            # All numbers are deserialized as strings
            # Assume numeric strings were originally numbers, favouring int over float
            field_value = _maybe_to_number(field_value)
            if isinstance(field_value, str):
                field_value = await self._process_fn(field_value)
            return field_value
        elif self._ref_key in field_schema:
            field_record_name = self._parse_ref_schema_name(field_schema)
            field_schema = self._get_referenced_schema(field_record_name, ref_schemas)
            return await self.process_record(field_schema, field_value, *args)
        return field_value

    async def process_complex_type(self, field_name: str, type_schema: dict, field_value: Any, ref_schemas: dict,
                                   *args) -> Any:
        field_type = type_schema['type']

        if field_type == 'object':
            item_schema = type_schema['additionalProperties']
            for key in field_value:
                field_value[key] = await self.process_field(field_name, item_schema, field_value[key], ref_schemas,
                                                            *args)
            return field_value

        if field_type == 'array':
            item_schema = type_schema['items']
        else:
            raise NotImplementedError

        if 'type' in item_schema:
            if item_schema['type'] == 'string':
                return [await self._process_fn(value) for value in field_value]
            if item_schema['type'] in self._complex_types:
                field_values = []
                for value in field_value:
                    sub_field_values = await self.process_field(field_name, item_schema, value, ref_schemas, *args)
                    field_values.append(sub_field_values)
                return field_values
        elif 'anyOf' in item_schema and dict(type='string') in item_schema['anyOf']:
            # All numbers are deserialized as strings
            # Assume numeric strings were originally numbers, favouring int over float
            field_values = []
            for value in field_value:
                value = _maybe_to_number(value)
                if isinstance(value, str):
                    value = await self._process_fn(value)
                field_values.append(value)
            return field_values
        elif self._ref_key in item_schema:
            field_record_name = self._parse_ref_schema_name(item_schema)
            field_schema = self._get_referenced_schema(field_record_name, ref_schemas)
            return [await self.process_record(field_schema, record, *args) for record in field_value]
        return field_value

    def _parse_ref_schema_name(self, full_schema: dict) -> str:
        return full_schema[self._ref_key].split('/')[-1]

    @staticmethod
    def _get_referenced_schema(schema_name: str, ref_schemas: dict) -> dict:
        assert ref_schemas
        field_schema = ref_schemas[schema_name]
        field_schema['definitions'] = deepcopy(ref_schemas)
        assert field_schema['type'] == 'object'
        return field_schema
