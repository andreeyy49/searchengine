package searchengine.worker;

import lombok.extern.slf4j.Slf4j;
import searchengine.model.*;
import searchengine.services.LemmaService;
import searchengine.utils.LemmasFinder;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveTask;

@Slf4j
public class PageIndexingWorker extends RecursiveTask<List<Lemma>> {

    private final Set<Page> pages;
    private final LemmaService lemmaService;
    private int size;
    private final int processorSize;
    private static Set<String> addedLemmasValues;
    private static Set<Lemma> addedLemmas;

    public PageIndexingWorker(Set<Page> pages, LemmaService lemmaService) {
        this.pages = ConcurrentHashMap.newKeySet();
        this.pages.addAll(pages);
        this.lemmaService = lemmaService;
        this.processorSize = getProcessorSize();
        addedLemmasValues = ConcurrentHashMap.newKeySet();
        addedLemmas = ConcurrentHashMap.newKeySet();
    }

    public PageIndexingWorker(Set<Page> pages, LemmaService lemmaService, int size) {
        this.pages = ConcurrentHashMap.newKeySet();
        this.pages.addAll(pages);
        this.lemmaService = lemmaService;
        this.size = size;
        this.processorSize = getProcessorSize();
    }

    @Override
    protected List<Lemma> compute() {

        List<Lemma> lemmasToReturn = new ArrayList<>();

        List<PageIndexingWorker> taskList = new ArrayList<>();

        if (size > (pages.size() / processorSize - 1)) {
            Set<Page> leftSet = splitSet(SetSide.LEFT);
            Set<Page> rightSet = splitSet(SetSide.RIGHT);

            PageIndexingWorker leftTask = new PageIndexingWorker(leftSet, this.lemmaService, this.size);
            PageIndexingWorker rightTask = new PageIndexingWorker(rightSet, this.lemmaService, this.size);

            if (!leftSet.isEmpty()) {
                leftTask.fork();
                taskList.add(leftTask);
            }
            if (!rightSet.isEmpty()) {
                rightTask.fork();
                taskList.add(rightTask);
            }

        } else {
            for (Page page : pages) {
                List<Lemma> lemmas = addLemmas(page);
                synchronized (lemmaService) {
                    List<Lemma> lemmaList = lemmaService.saveAll(lemmas);
                    addedLemmas.addAll(lemmaList);
                    log.info("added {} lemmas", lemmas.size());
                }
            }
        }

        for (PageIndexingWorker task : taskList) {
            if (task != null) {
                try {
                    List<Lemma> result = task.join();
                    if (result != null) {
                        lemmasToReturn.addAll(result);
                    }
                } catch (Exception e) {
                    log.error("Ошибка при выполнении задачи: {0}", e);
                }
            }
        }

        return lemmasToReturn;
    }

    private Set<Page> splitSet(SetSide setSide) {
        List<Page> pageList = new ArrayList<>(this.pages);
        List<Page> chunkPageList = new ArrayList<>();
        Set<Page> chunkSet = ConcurrentHashMap.newKeySet();
        int mid = pageList.size() / 2;

        if (setSide == SetSide.LEFT) {
            chunkPageList = pageList.subList(0, mid);
        } else if (setSide == SetSide.RIGHT) {
            chunkPageList = pageList.subList(mid, pageList.size());
        }

        chunkSet.addAll(chunkPageList);

        return chunkSet;
    }

    private List<Lemma> addLemmas(Page page) {
        List<Lemma> lemmasToSave = new ArrayList<>();

        try {
            HashMap<String, Integer> lemmas = LemmasFinder.getLemmasHashMap(page.getContent());

            for (HashMap.Entry<String, Integer> entry : lemmas.entrySet()) {
                Index index = new Index();
                index.setPage(page);
                index.setRank(entry.getValue());
                Lemma lemma = null;
                if (!addedLemmasValues.contains(entry.getKey())) {
                    lemma = new Lemma();
                    lemma.setLemma(entry.getKey());
                    lemma.setFrequency(1);
                    lemma.setSite(page.getSite());
                    List<Index> indexes = new ArrayList<>();
                    indexes.add(index);
                    lemma.setIndexes(indexes);
                    index.setLemma(lemma);
                    lemmasToSave.add(lemma);
                    addedLemmasValues.add(entry.getKey());
                } else {
                    while (lemma == null) {
                        log.info("WAIT");
                        lemma = addedLemmas.stream().filter(el -> el.getLemma().equals(entry.getKey())).findFirst().orElse(null);
                    }
                    lemma.setFrequency(lemma.getFrequency() + 1);
                    List<Index> indexes = lemma.getIndexes();
                    indexes.add(index);
                    lemma.setIndexes(indexes);
                    index.setLemma(lemma);
                    lemmasToSave.add(lemma);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return lemmasToSave;
    }

    private int getProcessorSize() {
        Runtime runtime = Runtime.getRuntime();

        return runtime.availableProcessors() + 1;
    }
}
