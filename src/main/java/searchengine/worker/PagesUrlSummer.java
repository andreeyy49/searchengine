package searchengine.worker;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.*;
import searchengine.services.*;
import searchengine.utils.LemmasFinder;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class PagesUrlSummer extends RecursiveTask<List<PageUrl>> {
    private final PageService pageService;
    private final SiteService siteService;
    private final LemmaService lemmaService;
    private final IndexService indexService;
    private final PageUrl address;
    private final IndexingService indexingService;
    private String headAddress;

    private static Set<String> addedUrls;
    private static Set<String> addedLemmasValues;
    private static List<Lemma> addedLemmas;

    private final Site parentSite;
    private static final Pattern HTTPS_PATTERN = Pattern.compile("https://[^/]+");
    private static final Pattern FULL_URL_PATTERN = Pattern.compile("https://[a-z]+[.a-z]+[/A-z-\\d()]*/[/a-z-\\d()]*");
    private static final Pattern RELATIVE_URL_PATTERN = Pattern.compile("/[a-z+]+[/A-z- \\d%#()+_.]*/([/a-z- \\d%#()+_.]*)*");

    public PagesUrlSummer(PageUrl address, PageService pageService, SiteService siteService, LemmaService lemmaService, IndexService indexService, IndexingService indexingService) {
        this.pageService = pageService;
        this.siteService = siteService;
        this.lemmaService = lemmaService;
        this.indexService = indexService;
        this.indexingService = indexingService;

        parentSite = null;
        Matcher matcher = HTTPS_PATTERN.matcher(address.getAbsolutePath());

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            headAddress = address.getAbsolutePath().substring(start, end);
        }

        if (address.getPath() == null) {
            address.setPath(address.getAbsolutePath());
        }

        address.setHeadUrl(headAddress);
        this.address = address;
        addedUrls = ConcurrentHashMap.newKeySet();
        addedLemmas = new ArrayList<>();
        addedLemmas.addAll(lemmaService.findAll());
        addedLemmasValues = ConcurrentHashMap.newKeySet();
        addedLemmasValues.addAll(lemmaService.findAllLemmaValue());
    }

    public PagesUrlSummer(PageUrl address, PageService pageService, SiteService siteService, String headAddress, Site parentSite, LemmaService lemmaService, IndexService indexService, IndexingService indexingService) {
        this.pageService = pageService;
        this.siteService = siteService;
        this.headAddress = headAddress;
        this.parentSite = parentSite;
        this.lemmaService = lemmaService;
        this.indexService = indexService;
        address.setHeadUrl(headAddress);
        this.address = address;
        this.indexingService = indexingService;
    }

    @Override
    protected List<PageUrl> compute() {
        List<PageUrl> urls = new ArrayList<>();
        connection();
        List<PagesUrlSummer> taskList = new ArrayList<>();

        Site site;

        if (parentSite == null) {
            site = siteService.findByUrl(headAddress);
        } else {
            site = parentSite;
        }

        Page page = new Page();
        page.setSite(site);
        page.setPath(address.getPath());
        String addressContent = address.getContent();

        page.setContent(addressContent);
        page.setCode(200);

        synchronized (pageService) {
            pageService.save(page);
            log.info("added page {}", page.getPath());
        }

        try {
            addLemmasAndIndexes(page, site);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        urls.add(address);

        for (PageUrl children : this.address.getChildren()) {
            if (children != null) {
                PagesUrlSummer task = new PagesUrlSummer(children, pageService, siteService, headAddress, site, lemmaService, indexService, indexingService);
                task.fork();
                taskList.add(task);
            }
        }

        for (PagesUrlSummer task : taskList) {
            if (task != null) {
                try {
                    List<PageUrl> result = task.join();
                    if (result != null) {
                        urls.addAll(result);
                    }
                } catch (Exception e) {
                    log.error("Ошибка при выполнении задачи: {0}", e);
                }
            }
        }

        return urls;
    }


    private void connection() {
        //--------------Подключаюсь--------------
        try {
            Thread.sleep(300);
            Document doc = Jsoup.connect(address.getAbsolutePath())
                    .userAgent("HeliontSearchBot/1.0")
                    .referrer("http://www.google.com")
                    .timeout(100000)
                    .get();

            this.address.setContent(doc.text());
            Elements element = doc.select("a");
            //--------------Ищу детей--------------
            for (Element el : element) {
                String addressChildren = el.toString();
                //--------------Фильтрую адреса--------------
                if (addressChildren.contains("Instagram") || addressChildren.contains(".jpg") ||
                        addressChildren.contains(".sql") ||
                        addressChildren.contains(".png") || addressChildren.contains("tilda/click")) {
                    continue;
                }
                if (addressChildren.contains("href=\"")) {
                    if (addressChildren.contains("https://") || addressChildren.contains("http://")) {
                        Matcher matcher = FULL_URL_PATTERN.matcher(addressChildren);
                        while (matcher.find()) {
                            int start = matcher.start();
                            int end = matcher.end();
                            if (addressChildren.charAt(end - 1) == '/') {
                                addressChildren = addressChildren.substring(start, end - 1);
                            } else {
                                addressChildren = addressChildren.substring(start, end);
                            }
                            //--------------Проверка уникальности адреса--------------
                            if (addedUrls.contains(addressChildren)) {
                                break;
                            }
                            addChildren(addressChildren, headAddress);

                            break;

                        }
                    } else {
                        Matcher matcher = RELATIVE_URL_PATTERN.matcher(addressChildren);
                        while (matcher.find()) {
                            int start = matcher.start();
                            int end = matcher.end();
                            addressChildren = addressChildren.substring(start + 1, end);
                            while (addressChildren.endsWith("/")) {
                                addressChildren = addressChildren.substring(0, addressChildren.length() - 1);
                            }
                            //--------------Проверка уникальности адреса-------------
                            if (addedUrls.contains("/" + addressChildren)) {
                                break;
                            }
                            addressChildren = headAddress + "/" + addressChildren;
                            addChildren(addressChildren, headAddress);
                            break;
                        }

                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            System.out.println(e.getMessage() + " address: " + this.address.getAbsolutePath() + " parent: " + this.address.getParent().getAbsolutePath());
        }
    }

    private void addChildren(String addressChildren, String headAddress) {
        //--------------Проверка не улетел ли я на левый сайт--------------
        String currentHeadAddress = "";
        Matcher matcher = HTTPS_PATTERN.matcher(addressChildren);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            currentHeadAddress = addressChildren.substring(start, end);

            break;
        }
        //--------------Инициализация нового ребенка--------------
        if (headAddress.equals(currentHeadAddress) && (!addressChildren.contains(".pdf") || !addressChildren.contains(".svg"))) {
            PageUrl newChildrenAddress = new PageUrl();
            newChildrenAddress.setChildren(new ArrayList<>());
            newChildrenAddress.setAbsolutePath(addressChildren + "/");
            newChildrenAddress.setParent(this.address);
            newChildrenAddress.setPath(addressChildren.replace(headAddress, ""));
            newChildrenAddress.setHeadUrl(headAddress);

            //--------------Добавление нового ребенка--------------
            if (!newChildrenAddress.getAbsolutePath().isBlank()) {
                addedUrls.add(newChildrenAddress.getPath());
                List<PageUrl> children = address.getChildren();
                children.add(newChildrenAddress);
                address.setChildren(children);
            }
        }
    }

    private Page addLemmasAndIndexes(Page page, Site site) throws IOException {
        HashMap<String, Integer> lemmas = LemmasFinder.getLemmasHashMap(page.getContent());

        List<Lemma> lemmasToSave = new ArrayList<>();
        List<Index> indexesToSave = new ArrayList<>();
        for (HashMap.Entry<String, Integer> entry : lemmas.entrySet()) {
            Index index = new Index();
            index.setPage(page);
            index.setRank(entry.getValue());
            Lemma lemma = new Lemma();
            if (!addedLemmasValues.contains(entry.getKey())) {
                lemma.setLemma(entry.getKey());
                lemma.setFrequency(1);
                lemma.setSite(site);
                List<Index> indexes = new ArrayList<>();
                indexes.add(index);
                lemma.setIndexes(indexes);
                index.setLemma(lemma);
                lemmasToSave.add(lemma);
                indexesToSave.add(index);
            } else {
                synchronized (lemmaService) {
                    lemma = lemmaService.findByLemma(entry.getKey());
                }
                lemma.setFrequency(lemma.getFrequency() + 1);
                List<Index> indexes = lemma.getIndexes();
                indexes.add(index);
                lemma.setIndexes(indexes);
                index.setLemma(lemma);
                lemmasToSave.add(lemma);
                indexesToSave.add(index);
            }
            addedLemmasValues.add(entry.getKey());
            log.info("Итерация");
        }
        synchronized (lemmaService) {
            synchronized (indexService) {
                lemmaService.saveAll(lemmasToSave);
                indexService.saveAll(indexesToSave);
            }
        }

        return page;
    }
}
