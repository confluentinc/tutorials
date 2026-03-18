import pkgutil
import subprocess
from typing import Dict, List

from pii.settings_utils import list_or_string_to_list


class SpacyModel:
    def __init__(self, lang_code, model_name):
        self.lang_code = lang_code
        self.model_name = model_name

    @classmethod
    def from_name(cls, model_name):
        lang_code = model_name[:2]
        return cls(lang_code=lang_code, model_name=model_name)

    def load(self):
        if pkgutil.find_loader(self.model_name):
            print(f"spacy model: {self.model_name} already present")
            return

        print(f"(down)loading spacy model: {self.model_name}")

        commands = ["python", "-m", "spacy", "download", self.model_name]
        process = subprocess.run([*commands], capture_output=True)
        if process.returncode != 0:
            raise EnvironmentError(f"Model not found (or unable to install) {self.model_name} - "
                                   f"{process.returncode} {process.stderr.decode()} {process.stdout.decode()}")


def load_models(model_names: List[str]) -> Dict:
    configuration = {
        "nlp_engine_name": "spacy",
        "models": [],
    }
    for model_name in model_names:
        model = SpacyModel.from_name(model_name)
        model.load()
        configuration["models"].append({"lang_code": model.lang_code, "model_name": model.model_name})

    return configuration


def init_models(models):
    # models is optional and can contain either a single model name in a string,
    # a List of model names
    # or a list of model names separated by commas
    models_to_load = list_or_string_to_list(models) if models else ['en_core_web_lg']
    return load_models(models_to_load)

