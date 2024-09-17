package searchengine.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.model.Lemma;
import searchengine.repository.LemmaRepository;
import searchengine.utils.BeanUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LemmaService {

    private final LemmaRepository lemmaRepository;

    public List<Lemma> findAll() {
        return lemmaRepository.findAll();
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

    public Lemma update(Lemma lemma) {
        Lemma oldLemma = findById(lemma.getId());
        BeanUtils.copyNotNullProperties(lemma, oldLemma);
        return lemmaRepository.save(oldLemma);
    }

    public void deleteById(Long id) {
        lemmaRepository.deleteById(id);
    }
}
