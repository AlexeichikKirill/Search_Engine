package searchengine.dto.indexing;

import lombok.Data;

@Data
public class IndexingResponseOk implements IndexingResponse{
    private boolean result = true;
}
