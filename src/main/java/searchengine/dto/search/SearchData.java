package searchengine.dto.search;

import lombok.Data;

@Data
public class SearchData implements Comparable<SearchData> {
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private float relevance;

    @Override
    public int compareTo(SearchData searchData) {
        if (relevance > searchData.getRelevance()) {
            return -1;
        }
        return 1;
    }
}
