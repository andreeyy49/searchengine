package searchengine.worker;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.Page;
import searchengine.model.PageUrl;
import searchengine.model.Site;
import searchengine.services.PageService;
import searchengine.services.SiteService;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class PagesUrlSummer extends RecursiveTask<List<PageUrl>> {
    private final PageService pageService;
    private final SiteService siteService;
    private final PageUrl address;
    private String headAddress;
    private final Site parentSite;
    private static final Pattern HTTPS_PATTERN = Pattern.compile("https://[^/]+");
    private static final Pattern FULL_URL_PATTERN = Pattern.compile("https://[a-z]+[.a-z]+[/A-z-\\d()]*/[/a-z-\\d()]*");
    private static final Pattern RELATIVE_URL_PATTERN = Pattern.compile("/[a-z+]+[/A-z- \\d%#()+_.]*/([/a-z- \\d%#()+_.]*)*");

    public PagesUrlSummer(PageUrl address, PageService pageService, SiteService siteService) {
        this.pageService = pageService;
        this.siteService = siteService;
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
    }

    public PagesUrlSummer(PageUrl address, PageService pageService, SiteService siteService, String headAddress, Site parentSite) {
        this.pageService = pageService;
        this.siteService = siteService;
        this.headAddress = headAddress;
        this.parentSite = parentSite;
        address.setHeadUrl(headAddress);
        this.address = address;
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
            if (pageService.findByPath(page.getPath()) != null) {
                return Collections.emptyList();
            }
            pageService.save(page);
            log.info("added page {}", page.getPath());
        }

        urls.add(address);

        for (PageUrl children : this.address.getChildren()) {
            if (children != null) {
                PagesUrlSummer task = new PagesUrlSummer(children, pageService, siteService, headAddress, site);
                taskList.add(task);
            }
        }

        invokeAll(taskList);

        for (PagesUrlSummer task : taskList) {
            if (task != null) {
                try {
                    List<PageUrl> result = task.join();
                    if (result != null) {
                        urls.addAll(result);  // Добавление только если result не null
                    }
                } catch (Exception e) {
                    log.error("Ошибка при выполнении задачи: {}", e.getMessage());
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

            this.address.setContent(doc.toString());
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
                List<PageUrl> children = address.getChildren();
                children.add(newChildrenAddress);
                address.setChildren(children);
            }
        }
    }
}
