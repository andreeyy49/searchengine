package searchengine.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.config.SitesList;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repository.PageRepository;
import searchengine.utils.BeanUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PageService {

    private final PageRepository repository;

    private final DataSource dataSource;

    private final SiteService siteService;

    private final SitesList sitesList;

    public List<Page> findAll() {
        return repository.findAll();
    }

    public Page findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException(MessageFormat.format("Page whit id:{0} not found", id)));
    }

    public Page findByPath(String path) {
        return repository.findByPath(path).orElse(null);
    }

    public Page save(Page page) {
        return repository.save(page);
    }

    public List<Page> saveAll(List<Page> pages) throws SQLException {
        String sql = "INSERT INTO pages (code, content, path, site_id) VALUES (?, ?, ?, ?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql)) {

            for (Page page : pages) {
                pstmt.setString(1, String.valueOf(page.getCode()));
                pstmt.setString(2, page.getContent());
                pstmt.setString(3, page.getPath());
                pstmt.setLong(4, page.getSite().getId());
                pstmt.addBatch();
            }

            pstmt.executeBatch();
            pstmt.close();
            connection.close();
            return pages;
        }
    }

    public Page update(Page page) {
        Page oldPage = findById(page.getId());
        BeanUtils.copyNotNullProperties(page, oldPage);
        return repository.save(oldPage);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public String getContent(String path) {
        Page page = findByPath(path);
        return page.getContent();
    }


    @SneakyThrows
    public Page initPage(String url, String pageUrl) {
        String siteAddress = url.replace(pageUrl, "");
        Page newPage = new Page();

        Document doc = Jsoup.connect(url)
                .userAgent("HeliontSearchBot/1.0")
                .referrer("http://www.google.com")
                .timeout(100000)
                .get();

        Site site = siteService.findByUrl(siteAddress);

        if(sitesList.findBySiteUrl(siteAddress) == null) {
            throw new IllegalStateException("Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле");
        }

        if(site == null)  {
            site = new Site();
            site.setUrl(siteAddress);
            site.setStatus(Status.INDEXED);
            site.setStatusTime(Instant.now());
            site.setName(sitesList.findBySiteUrl(siteAddress).getName());
            siteService.save(site);
        }

        newPage.setContent(doc.text());
        newPage.setSite(site);
        newPage.setPath(url.replace(siteAddress, ""));
        newPage.setCode(200);

        return newPage;
    }
}