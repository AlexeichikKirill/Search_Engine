package searchengine.siteparser.lemmatizator;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class Lemmatizer {

    private final LemmaFinder russianLF = LemmaFinder.getRussianInstance();
    private final LemmaFinder englishLF = LemmaFinder.getEnglishInstance();

    public String getLemmaWord(String word) {
        String language = checkLanguage(word);

        if (language.equals("Russian")) {
            List<String> lemmas = List.copyOf(russianLF.getRussianLemmaSet(word));
            if (lemmas.isEmpty())
                return "";
            return lemmas.get(0);
        } else if (language.equals("English")) {
            List<String> lemmas = List.copyOf(englishLF.getEnglishLemmaSet(word));
            if (lemmas.isEmpty())
                return "";
            return lemmas.get(0);
        }
        return "";
    }

    private String checkLanguage(String word) {
        word = word.toLowerCase(Locale.ROOT).replaceAll("([^a-zA-zА-Яа-я])", "");
        String russianAlphabet = "[а-яА-Я]+";
        String englishAlphabet = "[a-zA-z]+";

        if (word.matches(russianAlphabet)) {
            return "Russian";
        } else if (word.matches(englishAlphabet)) {
            return "English";
        } else {
            return "";
        }
    }

    public Map<String, Integer> getRussianMapLemmaCount(String string) {
        return russianLF.collectRussianLemmas(string);
    }

    public Map<String, Integer> getEnglishMapLemmaCount(String string) {
        return englishLF.collectEnglishLemmas(string);
    }
}
