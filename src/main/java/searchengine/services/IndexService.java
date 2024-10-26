package searchengine.services;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.model.Index;
import searchengine.repository.IndexRepository;
import searchengine.utils.BeanUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IndexService {

    private final IndexRepository indexRepository;

    public Index findById(Long id) {
        return indexRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Данная страница находится за пределами сайтов, указанных в конфигурационном файле"));
    }

    public Index save(Index index) {
        return indexRepository.save(index);
    }

    public List<Index> saveAll(List<Index> indexes) {
        return indexRepository.saveAll(indexes);
    }

    public Index update(Index index) {
        Index oldIndex = findById(index.getId());
        BeanUtils.copyNotNullProperties(index, oldIndex);
        return indexRepository.save(oldIndex);
    }

    public void deleteById(Long id) {
        indexRepository.deleteById(id);
    }
}
