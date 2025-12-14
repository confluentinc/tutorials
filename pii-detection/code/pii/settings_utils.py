from typing import List


# takes a string with a single element or several separated by commas
# and returns a List of strings
def string_to_list(text: str) -> List[str]:
    result = []
    if text.find(','):
        items = text.split(",")
        for i in items:
            result.append(i.strip())
    else:
        result.append(text)
    return result


# settings can contain either a single name in a string,
# a List of names
# or a list of names separated by commas
def list_or_string_to_list(settings):
    if isinstance(settings, str):
        return string_to_list(settings)
    elif isinstance(settings, list):
        return settings

    raise RuntimeError(f"Invalid format for settings: {settings}")