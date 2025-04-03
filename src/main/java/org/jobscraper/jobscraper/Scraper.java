package org.jobscraper.jobscraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Scraper {
    private final String keywords;
    private final String location;
    private final String distance;
    private final boolean useItSubpage;
    private final AtomicInteger offerCount = new AtomicInteger(0);
    private volatile boolean isCancelled = false;
    private final List<JobOffer> jobOffers = new ArrayList<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final List<String> offerUrlsList = new ArrayList<>();

    public Scraper(String keywords, String location, String distance, boolean useItSubpage) {
        this.keywords = keywords != null && !keywords.isEmpty() ? keywords : "";
        this.location = location != null && !location.isEmpty() ? location : "";
        this.distance = distance != null && !distance.isEmpty() ? distance : "0";
        this.useItSubpage = useItSubpage;
    }

    public void startScraping(HelloApplication ui) {
        new Thread(() -> {
            try {
                scrapeMainPages();
                scrapeJobOffers(ui);
                ui.finishScraping(offerCount.get());
            } catch (Exception e) {
                System.err.println("Error during scraping: " + e.getMessage());
            }
        }).start();
    }

    private void scrapeMainPages() throws IOException, InterruptedException {
        String baseUrl = useItSubpage ? "https://it.pracuj.pl/praca/" : "https://www.pracuj.pl/praca/";
        String urlParams = buildUrlParams();
        int page = 1;

        while (!isCancelled) {
            String url = baseUrl + urlParams + (page > 1 ? "&pn=" + page : "");
            System.out.println("Scraping page: " + url);
            Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(10000).get();
            Elements offerLinks = doc.select("a.tiles_cnb3rfy.core_n194fgoq");
            if (offerLinks.isEmpty()) break;

            for (Element link : offerLinks) {
                if (isCancelled) return;
                if (offerUrlsList.contains(link.attr("href"))) {return;}
                offerUrlsList.add(link.absUrl("href"));
            }
            System.out.println(offerUrlsList);
            page++;
            Thread.sleep(5000);
        }
    }

    private void scrapeJobOffers(HelloApplication ui) {
        ExecutorService offerExecutor = Executors.newFixedThreadPool(4);
        for (String offerUrl : offerUrlsList) {
            if (isCancelled) break;
            offerExecutor.submit(() -> scrapeOfferDetails(offerUrl, ui));
        }
        offerExecutor.shutdown();
        try {
            offerExecutor.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void scrapeOfferDetails(String offerUrl, HelloApplication ui) {
        try {
            Document offerDoc = Jsoup.connect(offerUrl).userAgent("Mozilla/5.0").timeout(10000).get();
            String title = offerDoc.selectFirst("h1") != null ? offerDoc.selectFirst("h1").text() : "Brak tytułu";
            String company = offerDoc.selectFirst("p[class*=company]") != null ? offerDoc.selectFirst("p[class*=company]").text() : "Brak firmy";
            String salary = offerDoc.selectFirst("div[class*=salary]") != null ? offerDoc.selectFirst("div[class*=salary]").text() : "Brak danych o zarobkach";
            String locationDetails = offerDoc.selectFirst("span[class*=location]") != null ? offerDoc.selectFirst("span[class*=location]").text() : "Brak lokalizacji";

            synchronized (jobOffers) {
                jobOffers.add(new JobOffer(title, company, salary, locationDetails, offerUrl));
                ui.updateOffersCount(offerCount.incrementAndGet());
            }
            System.out.println("Scraped: " + title);
        } catch (IOException e) {
            System.err.println("Error scraping offer " + offerUrl + ": " + e.getMessage());
        }
    }

    private String buildUrlParams() {
        StringBuilder params = new StringBuilder();
        if (!keywords.isEmpty()) {
            params.append(keywords.replace(" ", "%20")).append(";kw");
        }
        if (!location.isEmpty()) {
            params.append("/").append(location.replace(" ", "%20")).append(";wp");
        }
        if (!distance.equals("0")) {
            params.append("?rd=").append(distance);
        }
        return params.toString();
    }

    public void cancel() {
        isCancelled = true;
    }
}
