package searchengine.services;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.model.Site;
import searchengine.repository.SiteRepository;
import searchengine.utils.BeanUtils;

import java.text.MessageFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SiteService {

    private final SiteRepository repository;

    public List<Site> findAll() {
        return repository.findAll();
    }

    public Site findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException(MessageFormat.format("Site with id:{0} not found", id)));
    }

    public Site findByUrl(String url) {
        return repository.findByUrl(url).orElse(null);
    }

    public Site save(Site site) {
        return repository.save(site);
    }

    public Site update(Site site) {
        Site oldSite = findById(site.getId());
        BeanUtils.copyNotNullProperties(site, oldSite);
        return repository.save(site);
    }

    public void delete(Site site) {
        repository.delete(site);
    }

    public void deleteByUrl(String url) {
        repository.deleteByUrl(url);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
