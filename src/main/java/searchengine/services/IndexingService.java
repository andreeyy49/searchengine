package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.config.SiteConfig;
import searchengine.model.*;
import searchengine.worker.PagesUrlSummer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingService {

    private final SiteService siteService;

    private final PageService pageService;

    private final SitesList sites;

    private boolean isStartIndexing;

    public void startIndexing() throws ExecutionException, InterruptedException {

        long start = System.currentTimeMillis();

        if (isStartIndexing) {
            throw new IllegalStateException("Индексция уже запущена");
        }

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

        ForkJoinPool forkJoinPool = new ForkJoinPool(17);
        List<Future<List<PageUrl>>> pagesUrlSummerFuture = new ArrayList<>();

        for (PageUrl pageUrl : rootPageUrl) {
            pagesUrlSummerFuture.add(forkJoinPool.submit(new PagesUrlSummer(pageUrl, pageService, siteService)));
        }

        for (Future<List<PageUrl>> pagesUrlSummer : pagesUrlSummerFuture) {
            pagesUrlSummer.get();
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
                    pagesUrlSummer.cancel(true);
                    throw new IllegalStateException("Индексация остановлена пользователем");
                }
            }
        }

        for (SiteConfig siteConfig : siteConfigList) {
            Site site = sitesToDb.stream().filter(el -> el.getUrl().equals(siteConfig.getUrl())).findFirst().get();
            site.setStatus(Status.INDEXED);
            site.setStatusTime(Instant.now());
            siteService.update(site);
        }

        isStartIndexing = false;

        forkJoinPool.shutdownNow();

        log.info("Indexing finished " + (System.currentTimeMillis() - start) + "ms");
    }

    public void stopIndexing() {
        if (isStartIndexing) {
            isStartIndexing = false;
        } else {
            throw new IllegalStateException("Индексация не запущена");
        }
    }
}
