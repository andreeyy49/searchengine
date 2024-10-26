package searchengine.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class LemmasFinder {

    public static HashMap<String, Integer> getLemmasHashMap(String text) throws IOException {
        long start = System.currentTimeMillis();
        log.info("Starting get LemmasHashMap");

        text = Jsoup.parse(text).text();
        String[] wordsArray = text.split("\\s+|,\\s*|\\.\\s*|;\\s*");

        List<String> wordsList = new ArrayList<>(Arrays.asList(wordsArray));

        ConcurrentHashMap<String, Integer> lemmas = new ConcurrentHashMap<>();
        LuceneMorphology russianMorphology = new RussianLuceneMorphology();
        LuceneMorphology englishMorphology = new EnglishLuceneMorphology();

        for (String word : wordsList) {
            LuceneMorphology luceneMorph;
            if (word.matches("[А-Яа-я]+")) {
                luceneMorph = russianMorphology;
            } else if (word.matches("[A-Za-z]+")) {
                luceneMorph = englishMorphology;
            } else {
                continue;
            }
            List<String> wordBaseForms = luceneMorph.getNormalForms(word.toLowerCase(Locale.ROOT));
            List<String> wordBaseInfo = luceneMorph.getMorphInfo(word.toLowerCase(Locale.ROOT));

            wordBaseForms.forEach(baseForm -> {
                if (wordBaseInfo.get(0).contains("|l") || wordBaseInfo.get(0).contains("|n") || wordBaseInfo.get(0).contains("|o")) {
                    return;
                }
                if (lemmas.containsKey(baseForm)) {
                    lemmas.put(baseForm, lemmas.get(baseForm) + 1);
                } else {
                    lemmas.put(baseForm, 1);
                }
            });
        }

        log.info("Finishing get LemmasHashMap: {}ms", System.currentTimeMillis() - start);
        return new HashMap<>(lemmas);
    }

    public static String extractFragmentsWithHighlight(String html, List<String> lemmas) {
        Document document = Jsoup.parse(html);
        String text = document.text();

        // Регулярное выражение для всех лемм
        String lemmaRegex = String.join("|", lemmas);
        Pattern pattern = Pattern.compile(lemmaRegex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);

        // Храним все найденные совпадения с их позициями
        List<int[]> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new int[]{matcher.start(), matcher.end()});
        }

        if (matches.isEmpty()) {
            return "";
        }

        // Определяем стартовую и конечную позицию фрагмента
        int firstMatchStart = matches.get(0)[0];
        int lastMatchEnd = matches.get(matches.size() - 1)[1];

        // Увеличиваем длину фрагмента до примерно трех строк
        int start = Math.max(0, firstMatchStart - 20);
        int end = Math.min(text.length(), lastMatchEnd + 20);

        // Расширяем границы фрагмента до ближайших пробелов, чтобы не обрезать слова
        start = adjustToWordBoundary(text, start, -1);
        end = adjustToWordBoundary(text, end, 1);

        // Извлекаем фрагмент текста
        StringBuilder fragment = new StringBuilder(text.substring(start, end));

        // Заменяем все найденные леммы на выделенные жирным с помощью StringBuilder
        for (int i = matches.size() - 1; i >= 0; i--) {
            int[] match = matches.get(i);

            // Заменяем текст леммы на "<b>лемма</b>"
            fragment.insert(match[1] - start, "</b>");
            fragment.insert(match[0] - start, "<b>");
        }

        return fragment.toString();
    }

    private static int adjustToWordBoundary(String text, int index, int direction) {
        while (index > 0 && index < text.length() && Character.isLetterOrDigit(text.charAt(index))) {
            index += direction;
        }
        return index;
    }

}
