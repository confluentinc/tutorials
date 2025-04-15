#!/usr/bin/env python3

import argparse
import avro
from avro.io import DatumWriter, BinaryEncoder
import base64
import json
import io
import pathlib

parser = argparse.ArgumentParser()
parser.add_argument("--schema-path", help="path to Avro schema file")
parser.add_argument("--payload-path", help="path to payload to encode")
parser.add_argument("--schema-id", type=int, help="schema ID")
args = parser.parse_args()

schema = avro.schema.from_path(args.schema_path)

record = json.loads(pathlib.Path(args.payload_path).read_text())

buffer = io.BytesIO()

# write magic byte (zero)
buffer.write(b'\x00')

# write schema ID in next 4 bytes
buffer.write(args.schema_id.to_bytes(4))

# write the Avro-formatted data
DatumWriter(schema).write(record, BinaryEncoder(buffer))

print(base64.b64encode(buffer.getvalue()).decode())
