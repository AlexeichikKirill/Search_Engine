package searchengine.dto.statistics;

import lombok.Data;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.services.StatisticsService;

import java.util.Date;

@Data
public class DetailedStatisticsItem {
    private String url;
    private String name;
    private Status status;
    private Date statusTime;
    private String error;
    private int pages;
    private int lemmas;

    public DetailedStatisticsItem(Site site, StatisticsService statisticsService) {
        this.url = site.getUrl();
        this.name = site.getName();
        this.status = site.getStatus();
        this.statusTime = site.getStatusTime();
        this.error = site.getLastError() == null ? "undefined" : site.getLastError();
        this.pages = (int) statisticsService.getPageRepository().countBySite(site);
        this.lemmas = (int) statisticsService.getLemmaRepository().countBySite(site);
    }
}
