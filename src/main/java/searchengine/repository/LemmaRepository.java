package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Lemma;

import java.util.Optional;

public interface LemmaRepository extends JpaRepository<Lemma, Long> {
    Optional<Lemma> findByLemmaAndSite_Url(String lemma, String siteUrl);
}
