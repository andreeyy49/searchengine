package searchengine.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import searchengine.model.Lemma;
import searchengine.repository.LemmaRepository;
import searchengine.utils.BeanUtils;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LemmaService {

    private static final Logger log = LoggerFactory.getLogger(LemmaService.class);
    private final LemmaRepository lemmaRepository;

    public List<Lemma> findAll() {
        return lemmaRepository.findAll();
    }

    public List<String> findAllLemmaValue() {
        return findAll().stream().map(Lemma::getLemma).collect(Collectors.toList());
    }

    public ConcurrentHashMap<String, Lemma> getAllLemmasToMap() {
        ConcurrentHashMap<String, Lemma> lemmas = new ConcurrentHashMap<>();
        findAll().forEach(l -> lemmas.put(l.getLemma(), l));
        return lemmas;
    }

    public Lemma findById(Long id) {
        return lemmaRepository.findById(id).orElseThrow(()->new EntityNotFoundException("Lemma not found"));
    }

    public Lemma findByLemma(String lemma) {
        return lemmaRepository.findByLemma(lemma).orElse(null);
    }

    public Lemma save(Lemma lemma) {
        return lemmaRepository.save(lemma);
    }

    public List<Lemma> saveAll(List<Lemma> lemmaList) {
        return lemmaRepository.saveAll(lemmaList);
    }

    public Lemma update(Lemma lemma) {
        Lemma oldLemma = findById(lemma.getId());
        BeanUtils.copyNotNullProperties(lemma, oldLemma);
        return lemmaRepository.save(oldLemma);
    }

    public void deleteById(Long id) {
        lemmaRepository.deleteById(id);
    }
}
