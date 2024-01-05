import random
import time

from faker import Faker


Faker.seed(0)
fake_en = Faker('en_US')
fake_jp = Faker('ja_JP')
fake_fr = Faker('fr_FR')


def generate(fkr):
    return {'value':
        f"{fkr.paragraph(nb_sentences=2)} "
        f"{fkr.sbn9()} "
        f"{fkr.license_plate()} "
        f"{fkr.name()} "
        f"{fkr.address()} "
        f"{fkr.paragraph(nb_sentences=2)} "
    }


def poll(offsets):
    nb_results = random.randint(1, 10)
    time.sleep(1)
    results = []
    for _ in range(nb_results):
        results.append(generate(fake_en))
        results.append(generate(fake_jp))
        results.append(generate(fake_fr))

    return results

if __name__ == '__main__':
    print(poll(None))
