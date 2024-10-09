package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SitesList;
import searchengine.config.SiteConfig;
import searchengine.model.*;
import searchengine.utils.LemmasFinder;
import searchengine.worker.PageIndexingWorker;
import searchengine.worker.PagesUrlSummer;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingService {

    private final SiteService siteService;

    private final PageService pageService;

    private final LemmaService lemmaService;

    private final SitesList sites;

    private static boolean isStartIndexing;

    private final IndexService indexService;

    private static final Pattern HTTPS_PATTERN = Pattern.compile("https://[^/]+");

    private final int processorSize = Runtime.getRuntime().availableProcessors();

    public void startIndexing() {

        if (isStartIndexing) {
            throw new IllegalStateException("Индексция уже запущена");
        }

        Thread thread = new Thread(() -> {

            long start = System.currentTimeMillis();

            isStartIndexing = true;

            List<SiteConfig> siteConfigList = sites.getSiteConfigs();
            List<Site> sitesToDb = new ArrayList<>();

            for (SiteConfig siteConfig : siteConfigList) {
                Site newSite = new Site();
                newSite.setUrl(siteConfig.getUrl());
                newSite.setName(siteConfig.getName());
                newSite.setStatus(Status.INDEXING);
                newSite.setStatusTime(Instant.now());
                Site oldSite = siteService.findByUrl(newSite.getUrl());

                if (oldSite != null) {
                    siteService.deleteById(oldSite.getId());
                }

                sitesToDb.add(siteService.save(newSite));
            }


            List<PageUrl> rootPageUrl = new ArrayList<>();
            for (SiteConfig siteConfig : siteConfigList) {
                rootPageUrl.add(new PageUrl(siteConfig.getUrl()));
            }

            ForkJoinPool forkJoinPool = new ForkJoinPool(processorSize + 1);
            List<Future<List<PageUrl>>> pagesUrlSummerFuture = new ArrayList<>();
            Future<List<Lemma>> lemmaIndexingFuture;

            for (PageUrl pageUrl : rootPageUrl) {
                pagesUrlSummerFuture.add(forkJoinPool.submit(new PagesUrlSummer(pageUrl, pageService, siteService)));
            }

            for (Future<List<PageUrl>> pagesUrlSummer : pagesUrlSummerFuture) {
                while (!pagesUrlSummer.isDone()) {
                    if (!isStartIndexing) {
                        for (Site site : sitesToDb) {
                            site.setLastError("Индексация остановлена пользователем");
                            site.setStatus(Status.FAILED);
                            site.setStatusTime(Instant.now());
                            siteService.update(site);
                        }
                        forkJoinPool.shutdownNow();
                        log.info("Индексация остановлена пользователем");
                    }
                }
            }

            List<Page> pages = pageService.findAll();
            Set<Page> pageSet = new HashSet<>(pages);

            lemmaIndexingFuture = forkJoinPool.submit(new PageIndexingWorker(pageSet, lemmaService));

            while (!lemmaIndexingFuture.isDone()) {
                if (!isStartIndexing) {
                    for (Site site : sitesToDb) {
                        site.setLastError("Индексация остановлена пользователем");
                        site.setStatus(Status.FAILED);
                        site.setStatusTime(Instant.now());
                        siteService.update(site);
                    }
                    forkJoinPool.shutdownNow();
                    log.info("Индексация остановлена пользователем");
                }
            }

            for (SiteConfig siteConfig : siteConfigList) {
                Site site = sitesToDb.stream().filter(el -> el.getUrl().equals(siteConfig.getUrl())).findFirst().get();
                site.setStatus(Status.INDEXED);
                site.setStatusTime(Instant.now());
                siteService.update(site);
            }

            isStartIndexing = false;

            log.info("Indexing finished " + (System.currentTimeMillis() - start) + "ms");
        });

        thread.start();
    }

    public void stopIndexing() {
        if (isStartIndexing) {
            isStartIndexing = false;
        } else {
            throw new IllegalStateException("Индексация не запущена");
        }
    }

    @SneakyThrows
    public void indexPage(String url) {
        long startTime = System.currentTimeMillis();

        String pageUrl;

        String siteUrl = "";

        Matcher matcher = HTTPS_PATTERN.matcher(url);

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            siteUrl = url.substring(start, end);
        }

        pageUrl = url.replace(siteUrl, "");

        Page page = pageService.findByPath(pageUrl);

        if (page != null) {
            pageService.delete(page.getId());
        } else {
            page = pageService.initPage(url, pageUrl);
            pageService.save(page);
        }

        String content = page.getContent();

        HashMap<String, Integer> lemmas = LemmasFinder.getLemmasHashMap(content);

        log.info("Starting get LemmasHashMap");

        List<String> lemmasInDb = lemmaService.findAllLemmaValue();
        if (lemmasInDb == null) {
            lemmasInDb = new ArrayList<>();
        }

        List<Lemma> lemmasToSave = new ArrayList<>();
        List<Index> indexesToSave = new ArrayList<>();

        for (HashMap.Entry<String, Integer> entry : lemmas.entrySet()) {
            Index index = new Index();
            index.setPage(page);
            index.setRank(entry.getValue());
            if (!lemmasInDb.contains(entry.getKey())) {
                Lemma lemma = new Lemma();
                lemma.setLemma(entry.getKey());
                lemma.setFrequency(1);
                lemma.setSite(page.getSite());
                List<Index> indexes = new ArrayList<>();
                indexes.add(index);
                lemma.setIndexes(indexes);
                index.setLemma(lemma);
                lemmasToSave.add(lemma);
                indexesToSave.add(index);
            } else {
                Lemma lemma = lemmaService.findByLemma(entry.getKey());
                lemma.setFrequency(lemma.getFrequency() + 1);
                List<Index> indexes = lemma.getIndexes();
                indexes.add(index);
                lemma.setIndexes(indexes);
                index.setLemma(lemma);
                lemmasToSave.add(lemma);
                indexesToSave.add(index);
            }
            lemmasInDb.add(entry.getKey());
        }

        lemmaService.saveAll(lemmasToSave);
        indexService.saveAll(indexesToSave);

        log.info("indexing finished {}ms", System.currentTimeMillis() - startTime);
    }

    private List<HashMap<String, Integer>> splitHashmap(HashMap<String, Integer> hashMap) {
        List<HashMap<String, Integer>> listMap = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            listMap.add(new HashMap<>());
        }

        int count = 0;
        for (Map.Entry<String, Integer> entry : hashMap.entrySet()) {
            int index = count % 4;
            if (index == 0) {
                listMap.get(index).put(entry.getKey(), entry.getValue());
            } else if (index == 1) {
                listMap.get(index).put(entry.getKey(), entry.getValue());
            } else if (index == 2) {
                listMap.get(index).put(entry.getKey(), entry.getValue());
            } else if (index == 3) {
                listMap.get(index).put(entry.getKey(), entry.getValue());
            }
            count++;
        }

        return listMap;
    }

}