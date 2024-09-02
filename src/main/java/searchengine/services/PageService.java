package searchengine.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.model.Page;
import searchengine.repository.PageRepository;
import searchengine.utils.BeanUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PageService {

    private final PageRepository repository;

    private final DataSource dataSource;

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
}
