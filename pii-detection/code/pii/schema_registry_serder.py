import struct

from schema_registry.serializers.errors import SerializerError
from schema_registry.serializers.message_serializer import ContextStringIO, MAGIC_BYTE


def decode_schema_id(message: bytes) -> int:
    """
    Decode the schema ID from a message from kafka that has been encoded for use with the schema registry.
    This function is an extension to the python-schema-registry-client, which only provides the deserialised message.
    Args:
        message: message to be decoded
    Returns:
        dict: The ID of the schema the message was encoded with
    """
    if len(message) <= 5:
        raise SerializerError("message is too small to decode")
    with ContextStringIO(message) as payload:
        magic, schema_id = struct.unpack(">bI", payload.read(5))
        if magic != MAGIC_BYTE:
            raise SerializerError("message does not start with magic byte")
        return schema_id
