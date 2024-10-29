package searchengine.controllers;

import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.*;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;

    private final IndexingService indexingService;

    public ApiController(StatisticsService statisticsService, IndexingService indexingService) {
        this.indexingService = indexingService;
        this.statisticsService = statisticsService;
    }

    @GetMapping("/statistics")
    public StatisticsResponse statistics() {
        return statisticsService.getStatistics();
    }

    @GetMapping("/startIndexing")
    public IndexingResponse startIndexing() {
        indexingService.startIndexing();
        return new IndexingResponse(true);
    }

    @GetMapping("/stopIndexing")
    public IndexingResponse stopIndexing() {
        indexingService.stopIndexing();
        return new IndexingResponse(true);
    }

    @PostMapping("/indexPage")
    public IndexingResponse indexPage(@RequestParam String url) {
        indexingService.indexPage(url);
        return new IndexingResponse(true);
    }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam String query,
                                 @RequestParam @Nullable String site,
                                 @RequestParam @Nullable Integer offset,
                                 @RequestParam @Nullable Integer limit) {
        List<DataResponse> responseList = indexingService.search(query, site, offset, limit);
        return new SearchResponse(true, responseList.size(), responseList);
    }
}
