package searchengine.dto.indexing;

import lombok.Data;

@Data
public class IndexingResponseBad implements IndexingResponse{
    private boolean result = false;
    private String error;
}
