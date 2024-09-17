package searchengine;

import searchengine.utils.LemmasFinder;

import java.io.IOException;

public class Test {

    public static void main(String[] args) throws IOException {
        LemmasFinder lemmasFinder = new LemmasFinder();
        lemmasFinder.getLemmasHashMap("Повторное появление леопарда в Осетии позволяет предположить," +
                "что леопард постоянно обитает в некоторых районах Северного " +
                "Кавказа.");
    }
}
