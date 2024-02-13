import random
import time

from faker import Faker


Faker.seed(0)
fake = Faker(['it_IT', 'en_US', 'ja_JP', 'fr_FR'])

def poll(offsets):
    nb_results = random.randint(10, 100) # from 10 to 100 results returned
    time.sleep(1)
    results = []
    for _ in range(nb_results):
        results.append({'value':{
            'trap_id': fake.sbn9(),
            'speed': random.randint(500, 1500)/10,
            'license_plate': fake.license_plate(),
            'date_time': fake.iso8601(),
            'driver_name': fake.name(),
            'driver_address': fake.address(),
        }})

    return results
