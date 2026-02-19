import json
from typing import Dict, List, Tuple, Union


def is_json(some_string):
    try:
        json.loads(some_string)
    except ValueError:
        return False
    return True


class SchemaExplorer:
    @staticmethod
    def iterate_fields(record_dict: Dict, results: List, name_prefix: str = ""):
        for field_name, field_value in record_dict.items():
            if isinstance(field_value, dict):
                SchemaExplorer.iterate_fields(field_value, results, f"{field_name}.")
            elif isinstance(field_value, str):
                results.append((f"{name_prefix}{field_name}", field_value))

    def __init__(self, source_object):
        self.text_fields = []
        self.source_object = source_object
        self.iterate_fields(self.source_object, self.text_fields)

    def all_text_values(self) -> List[str]:
        return [i[1] for i in self.text_fields]

    def all_text_fields(self) -> List[Tuple[str, str]]:
        return [(i[0], i[1]) for i in self.text_fields]

    def get_parent_from_field_path_list(self, field_items: List[str]):
        field_items = field_items[:-1]
        result = self.source_object
        try:
            for item in field_items:
                result = result[item]
            return result
        except KeyError:
            return None

    def update_field_from_dotted(self, dotted_field_name: str, new_value):
        field_items = dotted_field_name.split('.')

        if not field_items or len(field_items) == 0 or not field_items[0]:
            return False

        field_name = field_items[-1]
        parent = self.get_parent_from_field_path_list(field_items)
        # the 2nd test is to ensure we don't create a new entry (on the next code line) if it doesn't already exist
        if parent and parent.get(field_name):
            parent[field_name] = new_value
            return True
        return False
