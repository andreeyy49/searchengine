package searchengine.worker;

import lombok.extern.slf4j.Slf4j;
import searchengine.model.*;
import searchengine.services.LemmaService;
import searchengine.services.RedisLemmaService;
import searchengine.utils.LemmasFinder;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveTask;

@Slf4j
public class PageIndexingWorker extends RecursiveTask<List<Lemma>> {

    private final Set<Page> pages;
    private final Site site;

    private final LemmaService lemmaService;
    private final RedisLemmaService redisLemmaService;


    public PageIndexingWorker(Set<Page> pages, Site site, LemmaService lemmaService, RedisLemmaService redisLemmaService) {
        this.pages = ConcurrentHashMap.newKeySet();
        this.pages.addAll(pages);
        this.site = site;
        this.lemmaService = lemmaService;
        this.redisLemmaService = redisLemmaService;
    }

    @Override
    protected List<Lemma> compute() {
        try {
            List<Lemma> lemmasToReturn = new ArrayList<>();
            if (pages.size() == 1) {
                for (Page page : pages) {
                    long start = System.currentTimeMillis();
                    List<Lemma> lemmas = addLemmas(page);
                    try {
                        synchronized (lemmaService) {
                            lemmaService.saveAll(lemmas);
                        }
                    } catch (Exception e) {
                        log.error(e.getMessage());
                    }
                    log.info("added {} lemmas, is: {}ms", lemmas.size(), System.currentTimeMillis() - start);
                    lemmasToReturn.addAll(lemmas);
                }
            } else {
                List<PageIndexingWorker> tasks = new ArrayList<>();
                tasks.add(new PageIndexingWorker(splitSet(SetSide.LEFT), site, lemmaService, redisLemmaService));
                tasks.add(new PageIndexingWorker(splitSet(SetSide.RIGHT), site, lemmaService, redisLemmaService));
                invokeAll(tasks);

                for (PageIndexingWorker task : tasks) {
                    if (task != null) {
                        List<Lemma> results = task.join();
                        if(results != null) {
                            lemmasToReturn.addAll(results);
                        }
                    }
                }
                return lemmasToReturn;
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            e.printStackTrace();
        }
        return null;
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
                Lemma lemma = new Lemma();
                Index index = new Index();
                index.setPage(page);
                index.setRank(entry.getValue());
                lemma.setLemma(entry.getKey());
                lemma.setFrequency(1);
                lemma.setSite(site);
                List<Index> indexes = new ArrayList<>();
                indexes.add(index);
                lemma.setIndexes(indexes);
                index.setLemma(lemma);
                lemmasToSave.add(lemma);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.info("Executing task in thread: {}", Thread.currentThread().getName());
        return lemmasToSave;
    }
}
