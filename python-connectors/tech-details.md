# CSID Python Tools

## Introduction 

The interface from java to python is done using the pemja library (developed by Alibaba).

https://github.com/alibaba/pemja

How pemja works:
- It starts a thread for the python interpreter in the JVM
- It calls python code using Java to C interface (JNI) and a C python module

## 1. Python SMT

Main java class = `io.confluent.pytools.PyConnectSmt`

Step 1. Add the Python Tools jar to the `CLASSPATH`.

Step 2. Put your python scripts, including an optional `requirements.txt` in a directory (the scripts directory).

Step 3. Add the following properties to the connector you're adding the SMT to:

(full description of the properties later in this document)

```json
"transforms": "myTransform",
"transforms.myTransform.type": "io.confluent.pytools.PyConnectSmt",
"transforms.myTransform.scripts.dir": "/app/",
"transforms.myTransform.working.dir": "/temp/venv/",
"transforms.myTransform.entry.point", "transform1.transform",
"transforms.myTransform.init.method", "init",
"transforms.myTransform.private.settings", "{\"conf1\":\"value1\", \"conf2\":\"value2\"}"

```

### How it works

👉 When initializing (during the `configure()` call of the java SMT SDK), the SMT builds a python virtual environment 
and (pip) installs the libraries referenced in the `requirements.txt` file. Then, it calls the `init()` method of the 
python script (if configured). It also installs the `pemja` library in the virtual environment.

For each call of the `apply()` call of the java SMT SDK (the method calling the transformation), it calls the `entry.point` 
python method and passes a dict with the Kafka record (see next section).

### Python script and method signatures

For transforms chained to source connectors, the python method called for the transform has the following signature:

`def transform(record)`

The format for `record` has 5 fields:

- topic: the name of the destination topic (can be modified by the transform)
- key_schema: the type of the key payload
- key: the key payload
- value_schema: the type of the value payload
- value: the value payload

Depending on their type, the payload format for keys and values varies: 
- When the payload is of a basic type (`string`, `integer`, `float`, `boolean`), it is passed as is (see below `key_schema` and `key`). 
- When the payload is a `JSON`, it is passed as a JSON object in a string (see below `value_schema` and `value`).

```json
{
  'topic': 'test-topic-641068b3-2a33-43d1-a5cb-9325a38ae5f7',
  'key_schema': 'INT32',
  'key': 0,
  'value_schema': 'JSON',
  'value': '{"first_name":"John","last_name":"Doe","age":"25"}'
}
```

### Filtering out a message

To filter out a message, the python transform can return `None`.

### Config properties

- `<transform.prefix>.type` must be `io.confluent.pytools.PyConnectSmt`
- `<transform.prefix>.scripts.dir`: the directory where the python scripts reside. 
- `<transform.prefix>.working.dir`: optional, the directory where to build the python virtual environment. If not passed, the scripts directory will be used.
- `<transform.prefix>.entry.point`: Python entry point for the transform. See details on python entry points below. 
- `<transform.prefix>.init.method`: optional, method called once by the SMT when it initializes the transform.
- `<transform.prefix>.private.settings`: String passed to the python script. Can be used to put settings in JSON format; eg. `"{\"conf1\":\"value1\", \"conf2\":\"value2\"}"`.
- `<transform.prefix>.offline.installation.dir`: optional, the directory containing wheel/python packages for offline installation of the packages in the virtual environment.

**Note on python entry points**

The format for the python entry points is driven by the way pemja works and the organization of python scripts inside the user modules. 

If the module doesn't have sub-modules, we'll import and call it this way:
```java
pyEnv.executePythonStatement("import <python-module-or-script>");
Object res = pyEnv.callPythonMethod("<python-module-or-script>.<python-method>");
```
So the entry point should be provided as `<python-module-or-script>.<python-method>`. 
For example, if the script is called `transform1.py` and the method is called `transform`, the entry point will be `transform1.transform`.

If there are sub-modules, we'll import and call it using an alias:

```java
pyEnv.executePythonStatement("import algorithms.strings as s1234");
res = pyEnv.callPythonMethod("s1234.decode_string", "3[a]2[bc]");
```

So the entry point should be provided as `algorithms.strings.decode_string` and we'll split it at the last dot and use an alias.



### Notes/FAQ

- How to provide packages for offline installation of the python environment? Put the wheel packages in a directory and provide it using `<transform.prefix>.offline.installation.dir`.
- The python script cannot/shouldn't change the type of the key or of the value.

## 2. Python Source Connector

Main java class = `io.confluent.pytools.PySourceConnector`

Step 1. Add the Python Tools jar to the `CLASSPATH`.

Step 2. Put your Python scripts, including an optional `requirements.txt` in a directory (the scripts directory).

Step 3. Add the following properties to the connector you're creating:

(full description of the properties later in this document)

```json
 {
  "name": "py-connect-01",
  "config": {
    "connector.class": "io.confluent.pytools.PySourceConnector",
    "tasks.max": "1",
    "topics": "topic-01",
    
    "value.converter": "io.confluent.connect.avro.AvroConverter",
    .../... <-- regular connect properties
    "value.converter.schema.registry.url": "http://localhost:8081",
    
    "scripts.dir": "/app/",
    "working.dir": "/temp/venv/",
    "entry.point", "connector01.poll",
    "init.method", "init",
    "private.settings", "{\"conf1\":\"value1\", \"conf2\":\"value2\"}"
  }
}
```

### How it works

See https://docs.confluent.io/platform/current/connect/devguide.html for Kafka Connect development concepts.

👉 When initializing the connector task, the connector builds a Python virtual environment
and (pip) installs the libraries referenced in the `requirements.txt` file. Then, it calls the `init()` method of the
Python script (if configured). It also installs the `pemja` library in the virtual environment.

For each call of the `poll()` call of the Java Source connector SDK, it calls the `entry.point`
Python method and expects a single Kafka record or a list of Kafka records (see next section).

### Python script and method signatures

For source connectors, the `poll()` method is called repeatedly and it's supposed to return a list of `SourceRecord` objects.
The `poll()` method has the following signature:

`def poll(offsets)`

- offset: the offsets (as a `dict<string, string>`). See next section for details.

The `poll()` method can return either a list of records (described below) or a single one. 

Records are expressed as Python dicts containing 2 keys: `key` and `value`.

The data for those keys is either directly a basic type (integer, float, boolean, string or bytes), or a dict (with keys and values of basic types).

For example:

```python

# key and value as a single basic type
def poll_basic_types(offsets):
    return [{
        'key': 1234,
        'value': "some string"
    },{
        'key':  4567,
        'value':  "another string"
    }]

# key and value are objects/dicts
def poll_objects(offsets):
    return [{
        'key': {
            'id': 1234,
            'type': 'something'
        },
        'value': {
            'first_name': 'John',
            'last_name': 'Doe',
            'age': 25
        }
    }, {
        'key': {
            'id': 567,
            'type': 'else'
        },
        'value': {
            'first_name': 'Jane',
            'last_name': 'Dolittle',
            'age': 37
        }
    }]

# keys or values are optional
def poll_no_key(offsets):
    return [{
        'key': None,
        'value': "some string"
    },{
        'key':  None,
        'value':  "another string"
    }]


# a single record can be returned
def poll_single(offsets):
    return {
        'key': None,
        'value': "some string"
    }

```

On the Java side, after the `poll()` method has been called, `SourceRecord` objects are created and sent to the destination topic.
From the python data, java stores the corresponding basic types:

- Python strings become `String` java objects.
- Python integers become `Long` (64-bit integers) java objects.
- Python floats become `Double` (64-bit floats) java objects.
- Python bytes arrays become `Byte` arrays.
- Python bools become `Boolean` java objects.

The `poll()` method can return `None` (java: `null`) if there's nothing to produce at the time of the call. The method will be called again later.

### Offset management

In addition to the `key` and `value` members, the records returned by the python `poll()` method can have an optional `offset` member.

The latest value of the offset returned by the `poll()` is stored by the Connect framework and passed to the `init()` method and at each invocation of the `poll()` method. 

This allows the connectors to resume work where they were in case the connector is stopped. 

For example:
```python
def poll_basic_types(offsets):
    print("offsets:")
    print(offsets)
    
    return [{
        'key': 1234,
        'value': 'some string',
        'offset': 123
    },{
        'key':  4567,
        'value':  'another string',
        'offset': 124
    }]
```

`print(offsets)` above returns:
```python
offsets:
{'latest': 122}
```

It can be of any basic type. 
If a batch has several records but not all of them have `offset` members, the last one in the batch will be recorded as the latest (even though it may not be associated with the last record in the batch). 

### Config properties

- `scripts.dir`: the directory where the python scripts reside.
- `working.dir`: optional, the directory where to build the python virtual environment. If not passed, the scripts directory will be used.
- `entry.point`: Python entry point for the connector (poll() method). See details on python entry points above.
- `init.method`: optional, method called once when it initializes the connector task.
- `private.settings`: String passed to the python script. Can be used to put settings in JSON format; eg. `"{\"conf1\":\"value1\", \"conf2\":\"value2\"}"`.
- `offline.installation.dir`: optional, the directory containing wheel/python packages for offline installation of the packages in the virtual environment.
