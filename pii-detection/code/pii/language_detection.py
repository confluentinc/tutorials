from typing import List

from lingua import Language, LanguageDetectorBuilder, IsoCode639_1


class LanguageDetector:
    DEFAULT_LANG_CODE = 'en'
    def __init__(self, lang_codes: List[str]):
        self.languages = [self.iso639_1_to_language(lang_code) for lang_code in lang_codes]
        if len(self.languages) == 0:
            self.detector = LanguageDetectorBuilder\
                .from_all_languages_without(Language.ESPERANTO, Language.LATIN)\
                .with_preloaded_language_models().build()
        else:
            self.detector = LanguageDetectorBuilder.from_languages(*self.languages).build()

    @staticmethod
    def iso639_1_to_language(code: str) -> Language:
        iso = IsoCode639_1[code.upper()]
        return Language.from_iso_code_639_1(iso)

    @staticmethod
    def language_to_iso639_1(lang: Language) -> str:
        iso = lang.iso_code_639_1
        return iso.name.lower()


    def detect_lang(self, text: str):
        lang = self.detector.detect_language_of(text)
        if not lang:
            return self.DEFAULT_LANG_CODE
        return self.language_to_iso639_1(lang)
