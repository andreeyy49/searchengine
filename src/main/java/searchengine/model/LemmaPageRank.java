package searchengine.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LemmaPageRank {

    private HashMap<Lemma, Integer> lemmaRank;

    private Page page;

    private Float totalRelevance;

    private Float thisRelevance;

    public LemmaPageRank(HashMap<Lemma, Integer> lemmaRank, Page page) {
        this.lemmaRank = lemmaRank;
        this.page = page;
        this.totalRelevance = (float) 0.0;
        this.thisRelevance = (float) 0.0;
    }

    public void calculateRelevance() {
        for(Map.Entry<Lemma, Integer> entry : lemmaRank.entrySet()) {
            thisRelevance += entry.getValue();
        }

    }

    public void calculateTotalRelevance() {
        this.totalRelevance = thisRelevance / totalRelevance;
    }
}
