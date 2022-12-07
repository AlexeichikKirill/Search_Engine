package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.siteparser.lemmatizator.Lemmatizer;
import searchengine.config.Config;
import searchengine.config.ConfigSite;
import searchengine.config.ConfigSitesList;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositoryes.IndexRepository;
import searchengine.repositoryes.LemmaRepository;
import searchengine.repositoryes.PageRepository;
import searchengine.repositoryes.SiteRepository;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Getter
@Setter
@Service
@RequiredArgsConstructor
public class SearchService {

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private IndexRepository indexRepository;

    @Autowired
    private LemmaRepository lemmaRepository;

    private String query;
    private SortedSet<Lemma> lemmaSortedSet = new TreeSet<>();
    private final Config config;
    private final ConfigSitesList sites;
    private final Lemmatizer lemmatizer = new Lemmatizer();
    private final Map<Page, Float> pageRankMap = new HashMap<>();
    private final Map<Page, Float> pageRelevanceMap = new HashMap<>();

    public SearchResponse getSearch(String query, String urlSite, int offset, int limit) {
        SearchResponse response = new SearchResponse();
        query = clearText(query);
        this.query = query;

        if (urlSite == null) {
            siteRepository.findAll().forEach(site -> response.addData(getSearchData(site)));
        } else {
            Site site = null;
            for (ConfigSite configSite : sites.getSites()) {
                if (configSite.getUrl().equals(urlSite)) {
                    site = siteRepository.findSiteByUrl(urlSite);
                    break;
                }
            }
            if (site == null) {
                SearchResponse responseBad = new SearchResponse();
                responseBad.setResult(false);
                return responseBad;
            }
            response.addData(getSearchData(site));
        }


        if (response.getCount() < offset) {
            SearchResponse responseBad = new SearchResponse();
            responseBad.setResult(false);
            return responseBad;
        }

        if (response.getCount() > limit) {
            SortedSet<SearchData> dataSortedSet = new TreeSet<>();
            for (SearchData data : response.getData()) {
                if (dataSortedSet.size() == limit) {
                    break;
                }
                dataSortedSet.add(data);
            }

            response.setData(dataSortedSet);
        }

        return response;
    }

    private SortedSet<SearchData> getSearchData(Site site) {
        List<Page> pageList = findPagesByQuery(this.query, site);

        SortedSet<SearchData> searchData = new TreeSet<>();

        pageList.forEach(page -> {
            Document document = Jsoup.parse(page.getContent());

            SearchData searchResult = new SearchData();
            searchResult.setSite(site.getUrl());
            searchResult.setSiteName(site.getName());
            searchResult.setUri(page.getPath());
            searchResult.setTitle(snippet(document.title()));
            searchResult.setSnippet(snippet(document.text()));
            searchResult.setRelevance(getRelevance(page));

            searchData.add(searchResult);
        });

        return searchData;
    }

    private String snippet(String inText) {
        String text = clearText(inText);
        StringBuilder snippet = new StringBuilder();
        List<String> tList = new ArrayList<>();
        List<String> qList = new ArrayList<>();
        Map<String, String> lemmaWordMap = new HashMap<>();

        for (String word : text.split("\\s+")) {
            String lemma = lemmatizer.getLemmaWord(word).toLowerCase(Locale.ROOT);
            tList.add(lemma);
            lemmaWordMap.put(lemma, word);
        }

        for (String word : query.split("\\s+")) {
            qList.add(lemmatizer.getLemmaWord(word).toLowerCase(Locale.ROOT));
        }

        List<Integer> foundsWordsIndexes = new ArrayList<>();
        for (String qWord : qList) {
            if (tList.contains(qWord)) {
                int indexOf = tList.indexOf(qWord);
                String wordInText = lemmaWordMap.get(qWord);
                lemmaWordMap.replace(qWord, wordInText, "<b>" + wordInText + "</b>");
                foundsWordsIndexes.add(indexOf);

                if (tList.size() <= 30) {
                    continue;
                }
                int startSL = indexOf < 10 ? indexOf : indexOf - 10;
                int endSL = startSL + 30 > tList.size() ? startSL : startSL + 30;

                if (startSL + 30 > tList.size()){
                    endSL = startSL + 20 > tList.size() ? startSL + 10 > tList.size() ? startSL : startSL + 10 : startSL;
                }

                tList = tList.subList(startSL, endSL);
            }
        }

        if (foundsWordsIndexes.isEmpty()) {
            if (inText.length() < 200) {
                return inText;
            }
            return null;
        }

        for (String tWord : tList) {
            snippet.append(lemmaWordMap.get(tWord)).append(" ");
        }
        return snippet.toString();
    }

    private void setRel(List<Page> pageList){
        pageList.forEach(page -> {
            AtomicReference<Float> absRel = new AtomicReference<>(0f);
            for (Lemma lemma : lemmaSortedSet) {
                indexRepository.findAllByLemma(lemma).forEach(index -> absRel.set(absRel.get() + index.getRank()));
            }
            pageRankMap.put(page, absRel.get());
        });

        float maxAbsRel = 0f;
        for (Float absRel : pageRankMap.values()) {
            if (maxAbsRel < absRel) {
                maxAbsRel = absRel;
            }
        }
        float finalMaxAbsRel = maxAbsRel;
        pageRankMap.forEach((page, rank) -> {
            float relevance = rank / finalMaxAbsRel;
            pageRelevanceMap.put(page, relevance);
        });
    }

    private float getRelevance(Page page) {
        return pageRelevanceMap.get(page);
    }

    private List<Page> findPagesByQuery(String query, Site site) {
        SortedSet<Lemma> lemmaSet = findLemmas(query, site);
        List<Page> pageList = new ArrayList<>();

        if (lemmaSet.isEmpty()) {
            return new ArrayList<>();
        }
        this.lemmaSortedSet = lemmaSet;
        List<Page> finalPageList = pageList;
        indexRepository.findAllByLemma(lemmaSet.first()).forEach(index -> finalPageList.add(index.getPage()));

        for (Lemma lemma : lemmaSet) {
            List<Page> pagIds = new ArrayList<>();

            for (Index index : indexRepository.findAllByLemma(lemma)) {
                if (pageList.contains(index.getPage())) {
                    pagIds.add(index.getPage());
                }

            }
            pageList = pagIds;
        }
        setRel(pageList);
        return pageList;
    }

    private SortedSet<Lemma> findLemmas(String query, Site site) {
        SortedSet<Lemma> lemmaSortedSet = new TreeSet<>();
        int countPages = (int) pageRepository.countBySite(site);

        for (String word : query.split("\\s+")) {
            String normalWord = lemmatizer.getLemmaWord(word);
            Lemma lemma = lemmaRepository.findByLemmaAndSite(normalWord, site);
            if (lemma == null) {
                continue;
            }

            boolean isFoundInLargeCountPages = (float) (lemma.getFrequency() / countPages) < 0.7;
            if (isFoundInLargeCountPages) {
                lemmaSortedSet.add(lemma);
            }
        }

        return lemmaSortedSet;
    }

    private String clearText(String text) {
        return text.replaceAll("[^А-Яа-яA-Za-z ]+", "");
    }
}
