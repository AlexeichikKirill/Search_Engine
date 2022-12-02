package searchengine.siteparser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import searchengine.siteparser.lemmatizator.Lemmatizer;
import searchengine.config.Config;
import searchengine.model.*;
import searchengine.repositoryes.IndexRepository;
import searchengine.repositoryes.LemmaRepository;
import searchengine.repositoryes.PageRepository;
import searchengine.repositoryes.SiteRepository;
import searchengine.services.IndexingServiceImpl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Pattern;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SiteParser extends RecursiveAction {

    private String url;
    private final Site site;
    private final Config config;
    private final Lemmatizer lemmatizer;
    private final List<String> visitUrl;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final List<SiteParser> parserList = new ArrayList<>();

    public SiteParser(Site site, IndexingServiceImpl indexingService) {
        this.site = site;
        visitUrl = new ArrayList<>();
        config = indexingService.getConfig();
        pageRepository = indexingService.getPageRepository();
        siteRepository = indexingService.getSiteRepository();
        lemmatizer = indexingService.getLemmatizer();
        lemmaRepository = indexingService.getLemmaRepository();
        indexRepository = indexingService.getIndexRepository();
    }

    private SiteParser(String url, SiteParser siteParser) {
        this.url = url;
        site = siteParser.site;
        config = siteParser.config;
        visitUrl = siteParser.visitUrl;
        pageRepository = siteParser.pageRepository;
        siteRepository = siteParser.siteRepository;
        lemmatizer = siteParser.lemmatizer;
        lemmaRepository = siteParser.lemmaRepository;
        indexRepository = siteParser.indexRepository;
    }

    @Override
    protected void compute() {
        try {
            Thread.sleep(200);
            String connectUrl = site.getUrl();
            connectUrl = url == null ? connectUrl : connectUrl + url;

            Document doc = Jsoup.connect(connectUrl)
                    .userAgent(config.getUserAgent())
                    .referrer(config.getReferrer()).get();

            if (url != null) {
                addPage(doc, url);
            }

            Elements elements = doc.select("body").select("a");

            for (Element a : elements) {
                String url = a.absUrl("href").replaceAll("\\?.+", "");
                if (isCorrectUrl(url) & !visitUrl.contains(url)) {
                    visitUrl.add(url);
                    SiteParser parser = new SiteParser(correctUrl(url), this);
                    parser.fork();
                    parserList.add(parser);
                }
            }

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        for (SiteParser parser : parserList) {
            parser.join();
        }
    }

    private void addPage(Document document, String url) {
        if (url.equals("")){
            return;
        }
        Site site = siteRepository.findSiteByUrl(this.site.getUrl());
        int code = document.connection().response().statusCode();
        site.setStatusTime(new Date());
        siteRepository.save(site);

        Page page = pageRepository.findByPath(url);
        if (page == null){
            page = new Page();
            page.setPath(url);
            page.setContent(document.html());
            page.setCode(code);
            page.setSite(site);
            pageRepository.save(page);
            if (code < 400){
                addLemmasAndIndexes(page, document);
            }
        }
    }

    private synchronized void addLemmasAndIndexes(Page page, Document document){
        String text = document.text();

        lemmatizer.getRussianMapLemmaCount(text).forEach((word, count) -> {
            if (word.length() > 4) {
                Lemma lemma = addLemma(word);
                addIndex(lemma, page, count);
            }
        });

        lemmatizer.getEnglishMapLemmaCount(text).forEach((word, count) -> {
            if (word.length() > 4) {
                Lemma lemma = addLemma(word);
                addIndex(lemma, page, count);
            }
        });
    }

    private Lemma addLemma(String word){
        Lemma lemma = lemmaRepository.findByLemmaAndSite(word, site);
        if (lemma == null) {
            lemma = new Lemma();
            lemma.setLemma(word);
            lemma.setFrequency(1);
            lemma.setSite(site);
        } else {
            lemma.setFrequency(lemma.getFrequency() + 1);
        }
        lemmaRepository.save(lemma);
        return lemma;
    }

    private void addIndex(Lemma lemma, Page page, int count){
        Index index = indexRepository.findByLemmaAndPage(lemma, page);
        if (index == null) {
            index = new Index();
            index.setLemma(lemma);
            index.setPage(page);
            index.setRank(count);
            indexRepository.save(index);
        }

    }

    private String correctUrl(String url) {
        url = url.replace(site.getUrl(), "").replace("//", "/");
        url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        return url;
    }

    private boolean isCorrectUrl(String url) {
        url = url.toLowerCase(Locale.ROOT);
        Pattern patternRoot = Pattern.compile("^" + site.getUrl());
        Pattern patternNotFile = Pattern.compile("([^\\s]+(\\.(?i)(jpg|png|gif|bmp|pdf))$)");
        Pattern patternNotAnchor = Pattern.compile("#([\\w\\-]+)?$");
        Pattern patternNotSort = Pattern.compile("/sort/");

        return patternRoot.matcher(url).lookingAt()
                && !patternNotFile.matcher(url).find()
                && !patternNotAnchor.matcher(url).find()
                && !patternNotSort.matcher(url).find();
    }

}
