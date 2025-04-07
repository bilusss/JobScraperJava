package org.jobscraper.jobscraper;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PracujPlScraper {
    private final String keywords;
    private final String location;
    private final List<String> offerLinks;
    private final Set<String> offerLinksSet;
    private final List<JobOffer> jobOffers;
    private final String distance;
    private int currentPage = 1;
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final HelloApplication ui;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private Thread scraperThread;
    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:123.0) Gecko/20100101 Firefox/123.0"
    };

    public PracujPlScraper(List<JobOffer> jobOffers, String keywords, String location,
                           List<String> offerLinks, Set<String> offerLinksSet, HelloApplication ui) {
        this.jobOffers = jobOffers;
        this.keywords = keywords != null && !keywords.isEmpty() ? keywords : "";
        this.location = location != null && !location.isEmpty() ? location : "";
        this.offerLinks = offerLinks;
        this.offerLinksSet = offerLinksSet;
        this.ui = ui;
        this.distance = "0"; // Default value
    }

    private String getRandomUserAgent() {
        int index = (int) (Math.random() * USER_AGENTS.length);
        return USER_AGENTS[index];
    }

    public void startScraping() {
        scraperThread = new Thread(() -> {
            try {
                while (!finished.get() && !cancelled.get()) {
                    scrapeMainPage();
                    Thread.sleep(2000); // Rate limiting
                    ui.updateUI(jobOffers.size(), offerLinksSet.size(), null);
                }
            } catch (Exception e) {
                System.err.println("[Pracuj.pl] Error: " + e.getMessage());
                e.printStackTrace();
            }
        });
        scraperThread.setDaemon(true);
        scraperThread.start();
    }

    private void scrapeMainPage() throws IOException, InterruptedException {
        if (cancelled.get()) {
            finished.set(true);
            return;
        }
        String baseUrl = "https://www.pracuj.pl/praca/";
        String urlParams = buildUrlParams();
        String url = baseUrl + urlParams + (currentPage > 1 ? "&pn=" + currentPage : "");
        System.out.println("[Pracuj.pl] Scraping page: " + url);

        Connection.Response response = Jsoup.connect(url)
                .userAgent(getRandomUserAgent())
                .timeout(10000)
                .followRedirects(true)
                .execute();

        // Check if there is a redirect
        if (response.hasHeader("Location")) {
            String redirectUrl = response.header("Location");
            if (redirectUrl.contains("it.pracuj.pl")) {
                System.out.println("[Pracuj.pl] Redirecting to: " + redirectUrl);
                response = Jsoup.connect(redirectUrl)
                        .userAgent(getRandomUserAgent())
                        .timeout(10000)
                        .followRedirects(true)
                        .execute();
            } else {
                System.out.println("[Pracuj.pl] Ignoring redirect to: " + redirectUrl);
                finished.set(true);
                return;
            }
        }

        Document doc = response.parse();
        String currentUrl = doc.location();
        if (!currentUrl.contains("pn=" + currentPage) && currentPage > 1) {
            System.out.println("[Pracuj.pl] No more pages available (redirected to: " + currentUrl + ")");
            finished.set(true);
            return;
        }

        Elements offerLinksElements = doc.select("a.tiles_cnb3rfy.core_n194fgoq");
        if (offerLinksElements.isEmpty()) {
            System.out.println("[Pracuj.pl] No more job offers found on page " + currentPage);
            finished.set(true);
            return;
        }

        for (Element link : offerLinksElements) {
            String offerUrl = link.absUrl("href");
            boolean isNewLink = offerLinksSet.add(offerUrl);
            if (isNewLink) {
                synchronized (offerLinks) {
                    offerLinks.add(offerUrl);
                }
            }
        }
        System.out.println("[Pracuj.pl] Found " + offerLinksSet.size() + " offers so far");
        currentPage++;
        Thread.sleep(4000); // Rate limiting
    }

    public void scrapeOfferDetails(String offerUrl) {
        try {
            try {
                Thread.sleep(2000); // Rate limiting
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            Document offerDoc = Jsoup.connect(offerUrl)
                    .userAgent(getRandomUserAgent())
                    .timeout(10000)
                    .get();

            String title = offerDoc.selectFirst("h1") != null ? offerDoc.selectFirst("h1").text() : "Brak tytułu";
            String company = offerDoc.selectFirst("p[class*=company]") != null ? offerDoc.selectFirst("p[class*=company]").text() : "Brak firmy";
            String salary = offerDoc.selectFirst("div[class*=salary]") != null ? offerDoc.selectFirst("div[class*=salary]").text() : "Brak danych o zarobkach";
            String locationDetails = offerDoc.selectFirst("span[class*=location]") != null ? offerDoc.selectFirst("span[class*=location]").text() : "Brak lokalizacji";
            String typeOfWork = offerDoc.selectFirst("div:contains(Typ umowy) + div") != null ? offerDoc.selectFirst("div:contains(Typ umowy) + div").text() : "Brak danych";
            String experience = offerDoc.selectFirst("div:contains(Poziom doświadczenia) + div") != null ? offerDoc.selectFirst("div:contains(Poziom doświadczenia) + div").text() : "Brak danych";
            String operatingMode = offerDoc.selectFirst("div:contains(Tryb pracy) + div") != null ? offerDoc.selectFirst("div:contains(Tryb pracy) + div").text() : "Brak danych";

            synchronized (jobOffers) {
                jobOffers.add(new JobOffer(title, company, salary, locationDetails, offerUrl, typeOfWork, experience, operatingMode));
            }
            if (jobOffers.size() == offerLinksSet.size()) {
                finished.set(true);
                ui.updateUI(jobOffers.size(), offerLinksSet.size(), null);
                System.out.println("[Pracuj.pl] All job offers scraped.");
            }
            ui.updateOffersCount(jobOffers.size());
        } catch (IOException e) {
            System.err.println("[Pracuj.pl] Error scraping offer " + offerUrl + ": " + e.getMessage());
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
        if (!distance.isEmpty()) {
            params.append("?rd=").append(distance);
        }
        return params.toString();
    }

    public boolean isFinished() {
        return finished.get();
    }

    public void cancel() {
        cancelled.set(true);
        if (scraperThread != null) {
            scraperThread.interrupt();
        }
        finished.set(true);
    }
}