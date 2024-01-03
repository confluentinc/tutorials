import logging
from pathlib import Path
from typing import List, Union

import yaml
from presidio_analyzer import AnalyzerEngine, RecognizerRegistry
from presidio_analyzer import PatternRecognizer
from presidio_analyzer.nlp_engine import NlpEngineProvider
from presidio_anonymizer import AnonymizerEngine
from spacy.util import fix_random_seed as spacy_fix_random_seed


class PresidioAnalyzer:
    def __init__(self, nlp_configuration,
                 entity_types: List,
                 extend_default_entity_types: bool = True,
                 custom_recognizers_yaml_path = None):

        random_seed = 456
        spacy_fix_random_seed(random_seed)

        default_entity_types = [
            'CREDIT_CARD',
            'EMAIL_ADDRESS',
            'IP_ADDRESS',
            'NRP',
            'LOCATION',
            'PERSON',
            'PHONE_NUMBER',
        ]
        if not entity_types:
            entity_types = default_entity_types.copy()
        elif extend_default_entity_types:
            entity_types.extend(default_entity_types)

        self.entities = entity_types

        self.supported_languages = [m["lang_code"] for m in nlp_configuration["models"]]

        self.nlp_engine = NlpEngineProvider(nlp_configuration=nlp_configuration).create_engine()

        self.registry = RecognizerRegistry()
        self.registry.load_predefined_recognizers(nlp_engine=self.nlp_engine, languages=self.supported_languages)

        if custom_recognizers_yaml_path:
            custom_recognizers = self.load_custom_recognizers_from_yaml(custom_recognizers_yaml_path)

            for recognizer in custom_recognizers:
                if recognizer.supported_language in self.supported_languages:
                    self.registry.add_recognizer(recognizer)
                    self.entities.extend(recognizer.supported_entities)
                else:
                    logging.warning(f'Recognizer {recognizer.name} will not be used as it is built for language '
                                    f'{recognizer.supported_language}, while this app is supporting languages: '
                                    f'{self.supported_languages}')

        self.analyzer = AnalyzerEngine(registry=self.registry,
                                       nlp_engine=self.nlp_engine,
                                       supported_languages=self.supported_languages)

    def analyze(self, raw_text, language):
        print(f"analysing in {language}: {raw_text}")
        return self.analyzer.analyze(raw_text, entities=self.entities, language=language)

    @staticmethod
    def load_custom_recognizers_from_yaml(recognizers_yaml_path: Union[str, Path]):
        if isinstance(recognizers_yaml_path, str):
            recognizers_yaml_path = Path(recognizers_yaml_path)
        assert recognizers_yaml_path.exists(), 'Path to YAML file containing custom recognizers does not exist: ' \
                                               f'{recognizers_yaml_path}'
        with recognizers_yaml_path.open() as yaml_file:
            serialized_recognizers = yaml.load(yaml_file, Loader=yaml.FullLoader)["recognizers"]
        return [PatternRecognizer.from_dict(serialised_recognizer) for serialised_recognizer in serialized_recognizers]


anonymizer = AnonymizerEngine()
