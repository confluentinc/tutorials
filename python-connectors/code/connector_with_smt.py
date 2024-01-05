import json
import random
import time


def init_connector(settings, offsets):
    print("source connector init() in python")
    print("settings:")
    print(json.loads(settings))
    print("offsets:")
    print(offsets)


def poll(offsets):
    sleep_time = random.randint(1, 1999)
    time.sleep(sleep_time/1000)

    return [{
        'key': sleep_time * 2,
        'value': f"some string - {sleep_time}"
    }]


def init_smt(settings):
    print("SMT init() in python")
    print(settings)

def transform(record):
    try:
        print("transform entry point in python")
        print(f"received: {record}")

        record['value'] = f"Modified from python SMT --> {record['value']}"
    except Exception as e:
        print("An exception occured:")
        print(e)

    return record
