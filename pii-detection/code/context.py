from dataclasses import dataclass

from pii.analyzer import PresidioAnalyzer
from pii.language_detection import LanguageDetector


@dataclass
class PiiSmtContext:
    lang_detector: LanguageDetector = None
    lang_code: str = "auto"
    analyzer: PresidioAnalyzer = None
    settings: dict = None
