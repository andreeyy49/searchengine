package searchengine.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.repository.LemmaRepository;
import searchengine.utils.BeanUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LemmaService {

    private final LemmaRepository lemmaRepository;

    private final RedisLemmaService redisLemmaService;

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
        return lemmaRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Lemma not found"));
    }

    public Lemma findByLemma(String lemma, String siteUrl) {
        return lemmaRepository.findByLemmaAndSite_Url(lemma, siteUrl).orElse(null);
    }

    public Lemma save(Lemma lemma) {
        return lemmaRepository.save(lemma);
    }

    @Transactional
    public void saveAll(List<Lemma> lemmaList) {
        lemmaList.forEach(lemma -> {
            Lemma existLemma = null;
            try {
                if(redisLemmaService.getLemmaFromCache(lemma.getLemma()).get() != null) {
                    existLemma = findByLemma(lemma.getLemma(), lemma.getSite().getUrl());
                }
            } catch (Exception e) {
                log.error(e.getMessage());
                e.printStackTrace();
            }
            if (existLemma != null) {
                lemma.setId(existLemma.getId());
                lemma.setFrequency(existLemma.getFrequency() + 1);
                List<Index> indexes = new ArrayList<>(existLemma.getIndexes());
                indexes.addAll(lemma.getIndexes());
                lemma.setIndexes(indexes);
                update(lemma);
            } else {
                save(lemma);
                redisLemmaService.saveLemmaToCache(lemma.getLemma(), lemma.getLemma());
            }
        });
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
