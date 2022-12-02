package searchengine.services;

import searchengine.dto.indexing.IndexingResponse;

public interface IndexingService {
    IndexingResponse getStartIndexing();
    IndexingResponse getStopIndexing();
    IndexingResponse getIndexPage(String url);
}
