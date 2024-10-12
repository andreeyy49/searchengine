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

//    private static Map<String, Lemma> addedLemmaMap;
    private static Set<String> addedLemmasValues;

    private final Object saveLock = new Object();
    private final Object checkLock = new Object();

    public PageIndexingWorker(Set<Page> pages, LemmaService lemmaService) {
        this.pages = ConcurrentHashMap.newKeySet();
        this.pages.addAll(pages);
        this.lemmaService = lemmaService;
        this.processorSize = getProcessorSize();

        addedLemmasValues = ConcurrentHashMap.newKeySet();
//        addedLemmaMap = new ConcurrentHashMap<>();
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
                long start = System.currentTimeMillis();
                List<Lemma> lemmas = addLemmas(page);
                synchronized (saveLock) {

                    lemmaService.saveAll(lemmas);

//                    updateBufferWithDbLemmas(lemmaService.saveAll(lemmas), addedLemmaMap);

                    log.info("added {} lemmas, is: {}ms", lemmas.size(), System.currentTimeMillis() - start);
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

//                if (addedLemmaMap.containsKey(entry.getKey())) {
//                    do {
//                        lemma = addedLemmaMap.get(entry.getKey());
//                    }
//                    while (lemma == null);
//                }

                if(addedLemmasValues.contains(entry.getKey())) {
                    synchronized (checkLock) {
                        lemma = lemmaService.findByLemma(entry.getKey());
                    }
                }

                if (lemma == null) {
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
//                    addedLemmaMap.put(entry.getKey(), lemma);
                } else {
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

    public void updateBufferWithDbLemmas(List<Lemma> dbLemmas, Map<String, Lemma> bufferMap) {
        for (Lemma dbLemma : dbLemmas) {
            if (bufferMap.containsKey(dbLemma.getLemma())) {
                Lemma bufferLemma = bufferMap.get(dbLemma.getLemma());
                bufferLemma.setFrequency(dbLemma.getFrequency());
                bufferLemma.setIndexes(dbLemma.getIndexes());
            } else {
                bufferMap.put(dbLemma.getLemma(), dbLemma);
            }
        }
    }
}
