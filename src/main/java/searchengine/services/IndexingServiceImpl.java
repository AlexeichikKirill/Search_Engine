package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.siteparser.lemmatizator.Lemmatizer;
import searchengine.siteparser.SiteParser;
import searchengine.config.Config;
import searchengine.config.ConfigSite;
import searchengine.config.ConfigSitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.indexing.IndexingResponseBad;
import searchengine.dto.indexing.IndexingResponseOk;
import searchengine.model.*;
import searchengine.repositoryes.IndexRepository;
import searchengine.repositoryes.LemmaRepository;
import searchengine.repositoryes.PageRepository;
import searchengine.repositoryes.SiteRepository;

import java.util.*;
import java.util.concurrent.ForkJoinPool;

@Getter
@Setter
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private IndexRepository indexRepository;

    @Autowired
    private LemmaRepository lemmaRepository;

    private final Config config;
    private final ConfigSitesList sites;
    private final Lemmatizer lemmatizer = new Lemmatizer();
    private final List<ForkJoinPool> poolList = new ArrayList<>();
    private final Map<Site, ForkJoinPool> sitePoolMap = new HashMap<>();

    @Override
    public IndexingResponse getStartIndexing() {
        List<Site> indexingSites = siteRepository.findAllByStatus(Status.INDEXING);

        if (indexingSites.isEmpty()) {
            new Thread(this::startIndexingAllSites).start();
            return new IndexingResponseOk();
        }

        IndexingResponseBad responseBad = new IndexingResponseBad();
        responseBad.setError("Индексация уже запущена");
        return responseBad;
    }

    public IndexingResponse getStopIndexing() {
        List<Site> indexingSites = siteRepository.findAllByStatus(Status.INDEXING);

        if (indexingSites.isEmpty()) {
            IndexingResponseBad responseBad = new IndexingResponseBad();
            responseBad.setError("\"Индексация не запущена\"");
            return responseBad;
        }

        poolList.forEach(ForkJoinPool::shutdown);
        poolList.clear();

        siteRepository.findAll().forEach(site -> {
            site.setLastError("Индексация остановлена пользователем");
            site.setStatusTime(new Date());
            site.setStatus(Status.FAILED);
            siteRepository.save(site);
        });
        return new IndexingResponseOk();
    }

    public IndexingResponse getIndexPage(String url) {
        ConfigSite configSite = null;
        for (ConfigSite s : sites.getSites()) {
            if (s.getUrl().equals(url)) {
                configSite = s;
                break;
            }
        }

        if (configSite == null) {
            IndexingResponseBad responseBad = new IndexingResponseBad();
            responseBad.setError("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            return responseBad;
        }

        ConfigSite finalConfigSite = configSite;
        new Thread(() -> indexPage(finalConfigSite)).start();
        return new IndexingResponseOk();
    }

    private void startIndexingAllSites() {
        if (!sites.getSites().isEmpty()) {
            sites.getSites().forEach(this::addSite);

            for (Site site : siteRepository.findAll()) {
                new Thread(() -> indexingOneSite(site)).start();
            }
        }
    }

    private void indexPage(ConfigSite configSite){
        for (Site site : siteRepository.findAllByStatus(Status.INDEXING)) {
            if (site.getUrl().equals(configSite.getUrl())) {
                sitePoolMap.get(site).shutdown();
                sitePoolMap.remove(site);
                addSite(configSite);
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        indexingOneSite(addSite(configSite));
    }

    private void indexingOneSite(Site site) {
        ForkJoinPool pool = new ForkJoinPool(2);
        poolList.add(pool);
        sitePoolMap.put(site, pool);

        SiteParser siteParser = new SiteParser(site, this);
        pool.invoke(siteParser);

        site = siteRepository.findSiteByUrl(site.getUrl());
        if (site.getStatus().equals(Status.INDEXING)) {
            site.setStatusTime(new Date());
            site.setStatus(Status.INDEXED);
            siteRepository.save(site);
            poolList.remove(pool);
        }
    }

    private Site addSite(ConfigSite site) {
        String url = site.getUrl();
        String name = site.getName();

        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        Site siteM = siteRepository.findSiteByUrl(url);
        if (siteM != null) {
            clearDataAboutSite(siteM);
        }

        siteM = new Site();
        siteM.setUrl(url);
        siteM.setName(name);
        siteM.setStatusTime(new Date());
        siteM.setStatus(Status.INDEXING);
        siteRepository.save(siteM);

        return siteM;
    }

    private synchronized void clearDataAboutSite(Site site) {
        List<Page> pageList = pageRepository.findAllBySite(site);
        if (!pageList.isEmpty()) {
            List<Index> indexList = new ArrayList<>();
            pageList.forEach(page -> indexList.addAll(indexRepository.findAllByPage(page)));
            indexRepository.deleteAll(indexList);
            pageRepository.deleteAll(pageList);
        }

        List<Lemma> lemmaList = lemmaRepository.findAllBySite(site);
        if (!lemmaList.isEmpty()) {
            lemmaRepository.deleteAll(lemmaList);
        }

        Site delSite = siteRepository.findSiteByUrl(site.getUrl());
        if (delSite != null) {
            siteRepository.delete(delSite);
        }

    }
}
