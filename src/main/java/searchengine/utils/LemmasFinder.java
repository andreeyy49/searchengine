package searchengine.utils;

import lombok.SneakyThrows;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.*;

public class LemmasFinder {

    public static HashMap<String, Integer> getLemmasHashMap(String text) throws IOException {
        String[] wordsArray = text.split("\\s+|,\\s*|\\.\\s*|;\\s*");

        List<String> wordsList = new ArrayList<>(Arrays.asList(wordsArray));

        HashMap<String, Integer> lemmas = new HashMap<>();

        for (String word : wordsList) {
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();
            List<String> wordBaseForms = luceneMorph.getNormalForms(word.toLowerCase(Locale.ROOT));
            List<String> wordBaseForm = luceneMorph.getMorphInfo(word.toLowerCase(Locale.ROOT));

            wordBaseForms.forEach(baseForm -> {
                if (wordBaseForm.get(0).contains("|l") || wordBaseForm.get(0).contains("|n") || wordBaseForm.get(0).contains("|o")) {
                    return;
                }
                if (lemmas.containsKey(baseForm)) {
                    lemmas.put(baseForm, lemmas.get(baseForm) + 1);
                } else {
                    lemmas.put(baseForm, 1);
                }
            });
        }

        return lemmas;
    }

}
