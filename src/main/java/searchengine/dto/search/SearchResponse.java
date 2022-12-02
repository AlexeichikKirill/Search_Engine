package searchengine.dto.search;

import lombok.Data;

import java.util.SortedSet;
import java.util.TreeSet;

@Data
public class SearchResponse {
    private boolean result = true;
    private SortedSet<SearchData> data = new TreeSet<>();
    private int count;

    public void addData(SortedSet<SearchData> searchData){
        data.addAll(searchData);
        count = data.size();
    }
}
