package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.config.SiteConfig;
import searchengine.dto.statistics.DataResponse;
import searchengine.model.*;
import searchengine.utils.LemmasFinder;
import searchengine.worker.PageIndexingWorker;
import searchengine.worker.PagesUrlSummer;

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

    private final RedisLemmaService redisLemmaService;

    private final SitesList sites;

    private static boolean isStartIndexing;

    private final IndexService indexService;

    private static final Pattern HTTPS_PATTERN = Pattern.compile("https://[^/]+");

    private final int processorSize = Runtime.getRuntime().availableProcessors();

    public void startIndexing() {

        if (isStartIndexing) {
            throw new IllegalStateException("Индексция уже запущена");
        }

        redisLemmaService.clearAllCaches();

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

            for (PageUrl pageUrl : rootPageUrl) {
                pagesUrlSummerFuture.add(forkJoinPool.submit(new PagesUrlSummer(pageUrl, pageService, siteService)));
            }

            for (Future<List<PageUrl>> pagesUrlSummer : pagesUrlSummerFuture) {
                while (!pagesUrlSummer.isDone()) {
                    stopWorkers(sitesToDb, forkJoinPool);
                }
            }

            List<Future<List<Lemma>>> tasksPageIndexWorkerFuture = new ArrayList<>();

            for (Site site : sitesToDb) {
                List<Page> pages = pageService.findBySite(site);

                tasksPageIndexWorkerFuture.add(forkJoinPool.submit(new PageIndexingWorker(new HashSet<>(pages), site, lemmaService, redisLemmaService)));
            }

            for (Future<List<Lemma>> task : tasksPageIndexWorkerFuture) {
                while (!task.isDone()) {
                    stopWorkers(sitesToDb, forkJoinPool);
                }
            }

            for (SiteConfig siteConfig : siteConfigList) {
                Site site = sitesToDb.stream().filter(el -> el.getUrl().equals(siteConfig.getUrl())).findFirst().get();
                site.setStatus(Status.INDEXED);
                site.setStatusTime(Instant.now());
                siteService.update(site);
            }

            isStartIndexing = false;

            log.info("Indexing finished {}ms", System.currentTimeMillis() - start);
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

            lemmasInDb.add(entry.getKey());
        }

        lemmaService.saveAll(lemmasToSave);
        indexService.saveAll(indexesToSave);

        log.info("indexing finished {}ms", System.currentTimeMillis() - startTime);
    }

    @SneakyThrows
    public List<DataResponse> search(String query, String site, Integer offset, Integer limit) {
        if (offset == null) {
            offset = 0;
        }
        if (limit == null) {
            limit = 20;
        }

        if(query.isEmpty()) {
            throw new IllegalStateException("Задан пустой поисковый запрос!");
        }

        HashMap<String, Integer> queryLemmas = LemmasFinder.getLemmasHashMap(query);
        List<Lemma> lemmasInDb = new ArrayList<>();
        List<Page> allPages = pageService.findAll();
        int totalPagesSize = allPages.size();
        List<String> sitesUrl = new ArrayList<>();

        if (site == null) {
            sitesUrl.addAll(siteService.findAll().stream().map(siteElement -> {
                if (siteElement.getStatus().equals(Status.INDEXED)) {
                    return siteElement.getUrl();
                }
                return null;
            }).filter(Objects::nonNull).toList());
        } else {
            sitesUrl.add(site);
        }

        if (sitesUrl.isEmpty()) {
            throw new IllegalStateException("Выбранные сайты не проиндексированны!");
        }


        for (String key : queryLemmas.keySet()) {
            for (String siteUrl : sitesUrl) {
                Lemma lemma = lemmaService.findByLemma(key, siteUrl);
                if (lemma != null && (checkPercent(80, totalPagesSize, lemma.getFrequency()) || queryLemmas.size() == 1)) {
                    lemmasInDb.add(lemma);
                }
            }
        }

        if(lemmasInDb.isEmpty()) {
            return new ArrayList<>();
        }

        lemmasInDb.sort(Comparator.comparing(Lemma::getFrequency));

        List<Long> firstLemmaIndexIds = new ArrayList<>();
        lemmasInDb.get(0).getIndexes().forEach(index -> firstLemmaIndexIds.add(index.getId()));
        List<Page> pagesResult = new ArrayList<>(pageService.findAllByIndexes(firstLemmaIndexIds));

        for (Lemma lemma : lemmasInDb) {
            List<Page> tempPages = new ArrayList<>();
            List<Long> indexIds = new ArrayList<>();
            if (lemma != lemmasInDb.get(0)) {
                lemma.getIndexes().forEach(index -> indexIds.add(index.getId()));
                List<Page> lemmasPages = new ArrayList<>(pageService.findAllByIndexes(indexIds));
                for (Page page : lemmasPages) {
                    if (pagesResult.contains(page)) {
                        tempPages.add(page);
                    }
                }
                pagesResult = new ArrayList<>(tempPages);
            }
        }

        List<LemmaPageRank> lemmasPageRanks = new ArrayList<>();

        for (Lemma lemma : lemmasInDb) {
            for (Index index : lemma.getIndexes()) {
                if (pagesResult.contains(index.getPage())) {
                    Page page = index.getPage();
                    boolean pageExists = false;

                    for (LemmaPageRank lemmaPageRank : lemmasPageRanks) {
                        if (lemmaPageRank.getPage().equals(page)) {
                            // Если сущность с этой страницей уже есть, добавляем новую лему в Map
                            lemmaPageRank.getLemmaRank().put(lemma, index.getRank());
                            pageExists = true;
                            break; // Останавливаем поиск, т.к. сущность найдена
                        }
                    }

                    // Если сущность с этой страницей не найдена, создаем новую
                    if (!pageExists) {
                        HashMap<Lemma, Integer> lemmaRanks = new HashMap<>();
                        lemmaRanks.put(lemma, index.getRank());
                        lemmasPageRanks.add(new LemmaPageRank(lemmaRanks, page));
                    }
                }
            }
        }

        lemmasPageRanks.forEach(LemmaPageRank::calculateRelevance);
        Float max = 0F;
        for (LemmaPageRank lemmaPageRank : lemmasPageRanks) {
            if (lemmaPageRank.getThisRelevance() > max) {
                max = lemmaPageRank.getThisRelevance();
            }
        }

        for (LemmaPageRank lemmaPageRank : lemmasPageRanks) {
            lemmaPageRank.setTotalRelevance(max);
            lemmaPageRank.calculateTotalRelevance();
        }

        lemmasPageRanks.sort(Comparator.comparing(LemmaPageRank::getTotalRelevance).reversed());
        List<DataResponse> dataResponses = new ArrayList<>();

        for (int i = offset; i < lemmasPageRanks.size(); i++) {
            if(i >= limit) {
                break;
            }
            DataResponse dataResponse = new DataResponse();

            dataResponse.setUri(lemmasPageRanks.get(i).getPage().getPath());
            dataResponse.setRelevance(lemmasPageRanks.get(i).getTotalRelevance());
            Site siteOnPage = lemmasPageRanks.get(i).getPage().getSite();
            dataResponse.setSite(siteOnPage.getUrl());
            dataResponse.setSiteName(siteOnPage.getName());
            String pageContent = lemmasPageRanks.get(i).getPage().getContent();
            Document document = Jsoup.parse(pageContent);
            dataResponse.setTitle(document.title());
            List<String> lemmaList = new ArrayList<>(lemmasPageRanks.get(i).getLemmaRank().keySet().stream().map(Lemma::getLemma).toList());
            pageContent = LemmasFinder.extractFragmentsWithHighlight(pageContent, lemmaList);

            if(pageContent.length() > 240) {
                pageContent = pageContent.substring(0, 270);
                while (!pageContent.endsWith(" ")) {
                    pageContent = pageContent.substring(0, pageContent.length() - 1);
                }
                if(pageContent.endsWith(",")) {
                    pageContent = pageContent.substring(0, pageContent.length() - 1);
                }
            }
            dataResponse.setSnippet(pageContent);

            dataResponses.add(dataResponse);
        }

        return dataResponses;

    }

    private boolean checkPercent(int percent, int sizePages, int frequency) {
        int result = frequency * 100 / sizePages;
        return percent >= result;
    }

    private void stopWorkers(List<Site> sitesToDb, ForkJoinPool forkJoinPool) {
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