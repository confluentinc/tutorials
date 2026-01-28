class TempReading(object):
    def __init__(self, device_id, temperature):
        self.device_id = device_id
        self.temperature = temperature

    @staticmethod
    def dict_to_reading(obj, ctx):
        if obj is None:
            return None

        return TempReading(device_id=obj['device_id'], temperature=obj['temperature'])

    @staticmethod
    def reading_to_dict(reading, ctx):
        return dict(device_id=reading.device_id, temperature=reading.temperature)
