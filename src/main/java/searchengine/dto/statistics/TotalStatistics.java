package searchengine.dto.statistics;

import lombok.Data;

@Data
public class TotalStatistics {
    private int sites = 0;
    private int pages = 0;
    private int lemmas = 0;
    private boolean indexing = false;
}
