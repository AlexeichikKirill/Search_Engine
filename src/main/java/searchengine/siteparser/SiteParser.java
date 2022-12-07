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
import searchengine.services.IndexingService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.RecursiveAction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SiteParser extends RecursiveAction {

    private String url;
    private final List<String> visitUrl;
    private final transient Logger logger;
    private final transient Site rootSite;
    private final transient Config config;
    private final transient Lemmatizer lemmatizer;
    private final transient IndexingService indexingService;
    private final transient PageRepository pageRepository;
    private final transient SiteRepository siteRepository;
    private final transient LemmaRepository lemmaRepository;
    private final transient IndexRepository indexRepository;

    public SiteParser(Site site, IndexingService indexingService) {
        this.rootSite = site;
        logger = indexingService.getLogger();
        visitUrl = new ArrayList<>();
        config = indexingService.getConfig();
        pageRepository = indexingService.getPageRepository();
        siteRepository = indexingService.getSiteRepository();
        lemmatizer = indexingService.getLemmatizer();
        lemmaRepository = indexingService.getLemmaRepository();
        indexRepository = indexingService.getIndexRepository();
        this.indexingService = indexingService;
    }

    private SiteParser(String url, SiteParser siteParser) {
        this.url = url;
        rootSite = siteParser.rootSite;
        config = siteParser.config;
        visitUrl = siteParser.visitUrl;
        pageRepository = siteParser.pageRepository;
        siteRepository = siteParser.siteRepository;
        lemmatizer = siteParser.lemmatizer;
        lemmaRepository = siteParser.lemmaRepository;
        indexRepository = siteParser.indexRepository;
        indexingService = siteParser.indexingService;
        logger = siteParser.logger;
    }

    @Override
    protected void compute() {
        if (indexingService.isStopFlag()) {
            return;
        }

        List<SiteParser> parserList = new ArrayList<>();

        try {
            Thread.sleep(200);
            String connectUrl = rootSite.getUrl();
            connectUrl = url == null ? connectUrl : connectUrl + url;

            Document doc = Jsoup.connect(connectUrl)
                    .userAgent(config.getUserAgent())
                    .referrer(config.getReferrer()).get();

            if (url != null) {
                addPage(doc, url);
            }

            Elements elements = doc.select("body").select("a");

            for (Element a : elements) {
                String href = a.absUrl("href").replaceAll("\\?.+", "");
                if (isCorrectUrl(href) && !visitUrl.contains(href)) {
                    visitUrl.add(href);
                    SiteParser parser = new SiteParser(correctUrl(href), this);
                    parser.fork();
                    parserList.add(parser);
                }
            }

        } catch (InterruptedException e) {
            logger.log(Level.WARNING, "Interrupted!", e);
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            logger.log(Level.WARNING, "HttpError!", e);
            Thread.currentThread().interrupt();
        }

        for (SiteParser parser : parserList) {
            parser.join();
        }
    }

    private void addPage(Document document, String url) {
        if (url.equals("")) {
            return;
        }
        Site site = siteRepository.findSiteByUrl(this.rootSite.getUrl());
        int code = document.connection().response().statusCode();
        site.setStatusTime(new Date());
        siteRepository.save(site);

        Page page = pageRepository.findByPath(url);
        if (page == null) {
            page = new Page();
            page.setPath(url);
            page.setContent(document.html());
            page.setCode(code);
            page.setSite(site);
            pageRepository.save(page);
            if (code < 400) {
                addLemmasAndIndexes(page, document);
            }
        }
    }

    private synchronized void addLemmasAndIndexes(Page page, Document document) {
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

    private Lemma addLemma(String word) {
        Lemma lemma = lemmaRepository.findByLemmaAndSite(word, rootSite);
        if (lemma == null) {
            lemma = new Lemma();
            lemma.setLemma(word);
            lemma.setFrequency(1);
            lemma.setSite(rootSite);
        } else {
            lemma.setFrequency(lemma.getFrequency() + 1);
        }
        lemmaRepository.save(lemma);
        return lemma;
    }

    private void addIndex(Lemma lemma, Page page, int count) {
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
        url = url.replace(rootSite.getUrl(), "").replace("//", "/");
        url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        return url;
    }

    private boolean isCorrectUrl(String url) {
        url = url.toLowerCase(Locale.ROOT);
        Pattern patternRoot = Pattern.compile("^" + rootSite.getUrl());
        Pattern patternNotFile = Pattern.compile("([^\\s]+(\\.(?i)(jpg|png|gif|bmp|pdf))$)");
        Pattern patternNotAnchor = Pattern.compile("#([\\w\\-]+)?$");
        Pattern patternNotSort = Pattern.compile("/sort/");

        return patternRoot.matcher(url).lookingAt()
                && !patternNotFile.matcher(url).find()
                && !patternNotAnchor.matcher(url).find()
                && !patternNotSort.matcher(url).find();
    }

}
