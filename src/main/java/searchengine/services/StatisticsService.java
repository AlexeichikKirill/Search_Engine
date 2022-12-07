package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositoryes.IndexRepository;
import searchengine.repositoryes.LemmaRepository;
import searchengine.repositoryes.PageRepository;
import searchengine.repositoryes.SiteRepository;

import java.util.List;

@Getter
@Setter
@Service
@RequiredArgsConstructor
public class StatisticsService {

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private IndexRepository indexRepository;

    @Autowired
    private LemmaRepository lemmaRepository;

    public StatisticsResponse getStatistics() {
        List<Site> siteList = siteRepository.findAll();

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData statistics = new StatisticsData();
        TotalStatistics total = new TotalStatistics();

        if (siteList.isEmpty()){
            statistics.setTotal(total);
            response.setStatistics(statistics);
            return response;
        }

        siteList.forEach(site -> {
            DetailedStatisticsItem detailed = new DetailedStatisticsItem(site, this);
            statistics.addDetailed(detailed);

            total.setPages(total.getPages() + detailed.getPages());
            total.setLemmas(total.getLemmas() + detailed.getLemmas());

            if (site.getStatus() == Status.INDEXING) {
                total.setIndexing(true);
            }
        });

        total.setSites(siteList.size());
        statistics.setTotal(total);
        response.setStatistics(statistics);

        return response;
    }
}
