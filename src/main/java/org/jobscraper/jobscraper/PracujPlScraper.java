package org.jobscraper.jobscraper;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    private final AtomicBoolean linksCollectionFinished = new AtomicBoolean(false);
    private Thread scraperThread;
    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 14_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1 Mobile/15E148 Safari/604.1",
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
                System.out.println("[Pracuj.pl] Finished scraping job offers.");
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
            linksCollectionFinished.set(true);
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

    public boolean isFinishedCollectingLinks() {
        return linksCollectionFinished.get();
    }

    public void scrapeOfferDetails(String offerUrl) {
        WebDriver offerDriver = null;
        try {
            Thread.sleep(2000); // Rate limiting

            if (cancelled.get()) {
                return;
            }

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--no-sandbox");
            options.addArguments("--headless");

            offerDriver = new ChromeDriver(options);
            offerDriver.get(offerUrl);

            // Czekanie na załadowanie strony
            WebDriverWait wait = new WebDriverWait(offerDriver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-test='text-positionName']")));

            // Wyciągnięcie danych za pomocą selektorów CSS i atrybutów data-test
            String title = getTextByCss(offerDriver, "[data-test='text-positionName']", "Brak tytułu");
            String company = getTextByCss(offerDriver, "[data-test='text-employerName']", "Brak firmy")
                    .replace("About the company", "").replace("O firmie", "").trim();
            String salary = getTextByCss(offerDriver, "[data-test='text-earningAmount']", "Undisclosed Salary");

            // Lokalizacja - szukamy elementu z data-test="sections-benefit-workplaces"
            String location = "Brak lokalizacji";
            try {
                WebElement locationElement = offerDriver.findElement(By.cssSelector("[data-test='sections-benefit-workplaces'] [data-test='offer-badge-title']"));
                location = locationElement.getText();
            } catch (Exception e) {
                try {
                    WebElement altLocationElement = offerDriver.findElement(By.cssSelector("[data-test='sections-benefit-workplaces'] [data-test='offer-badge-description']"));
                    location = altLocationElement.getText();
                } catch (Exception ignored) {}
            }

            // Typ umowy
            String typeOfWork = getTextByCss(offerDriver, "[data-test='sections-benefit-contracts'] [data-test='offer-badge-title']", "Brak danych");

            // Doświadczenie
            String experience = getTextByCss(offerDriver, "[data-test='sections-benefit-employment-type-name'] [data-test='offer-badge-title']", "Brak danych");

            // Tryb pracy
            String operatingMode = getTextByCss(offerDriver, "[data-scroll-id='work-modes'] [data-test='offer-badge-title']", "Brak danych");

            // Dodanie oferty do listy
            synchronized (jobOffers) {
                jobOffers.add(new JobOffer(title, company, salary, location, offerUrl, typeOfWork, experience, operatingMode));
            }

            System.out.println("[Pracuj.pl] Scraped job details: " + title);
            ui.updateOffersCount(jobOffers.size());

            if (jobOffers.size() == offerLinksSet.size()) {
                finished.set(true);
                ui.updateUI(jobOffers.size(), offerLinksSet.size(), null);
                System.out.println("[Pracuj.pl] All job offers scraped.");
            }

        } catch (Exception e) {
            System.err.println("[Pracuj.pl] Error scraping offer " + offerUrl + ": " + e.getMessage());
        } finally {
            if (offerDriver != null) {
                offerDriver.quit();
            }
        }
    }

    // Pomocnicza metoda do pobierania tekstu z użyciem CSS
    private String getTextByCss(WebDriver driver, String cssSelector, String defaultValue) {
        try {
            WebElement element = driver.findElement(By.cssSelector(cssSelector));
            String text = element.getText();
            return text.isEmpty() ? defaultValue : text;
        } catch (Exception e) {
            return defaultValue;
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