package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.Index;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;

public interface PageRepository extends JpaRepository<Page, Long> {

    Optional<Page> findByPath(String path);

    List<Page> findBySite(Site site);

    @Query("SELECT p FROM pages p " +
            "JOIN p.indexes i " +
            "WHERE i.id IN :indexIds")
    List<Page> findByIndexIds(@Param("indexIds") List<Long> indexIds);

}
