import json
from string import Template
from typing import List

from gql import Client, gql
from gql.transport.exceptions import TransportServerError
from gql.transport.requests import RequestsHTTPTransport

from typing import Set


class StreamCatalogError(TransportServerError):
    pass


class StreamCatalogTagQuery:
    def __init__(self, schema_registry_url: str, schema_registry_auth, field_tags: List[str]):
        catalog_query_url = f'{schema_registry_url}/catalog/graphql'
        transport = RequestsHTTPTransport(url=catalog_query_url, verify=True, retries=3, auth=schema_registry_auth)
        self._catalog_client = Client(transport=transport)
        self._tagged_fields_query_template = Template("""
            query {
              sr_field(tags: $field_tags, where: {id: {_eq: $schema_id}}) {
                name
              }
            }
        """)
        self._tagged_fields_query_template = Template(
            self._tagged_fields_query_template.safe_substitute(field_tags=json.dumps(field_tags)))

    def __call__(self, schema_id: int) -> Set[str]:
        query = self._tagged_fields_query_template.substitute(schema_id=schema_id)
        try:
            catalog_response = self._catalog_client.execute(gql(query))
        except TransportServerError as e:
            raise StreamCatalogError('GraphQL query to Stream Catalog failed') from e
        return {field['name'] for field in catalog_response['sr_field']} if catalog_response['sr_field'] else set()
