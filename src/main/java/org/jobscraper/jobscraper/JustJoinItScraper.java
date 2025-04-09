package org.jobscraper.jobscraper;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class JustJoinItScraper {
    private final WebDriverPool driverPool;
    private final List<JobOffer> jobOffers;
    private final String keywords;
    private final String location;
    private final Set<String> offerLinksSet; // Use Set to avoid duplicates
    private final List<String> offerLinks;
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final AtomicBoolean finishedScrolling = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicInteger scrollCounter = new AtomicInteger(0);
    private final int MAX_SCROLL_ATTEMPTS = 20; // Increased to capture more offers
    private final int SCROLL_WAIT_TIME = 2000; // milliseconds
    private WebDriver driver;
    private boolean driverInitialized = false;
    private final HelloApplication ui;
    private Thread scraperThread;

    // Rate limiting
    private final long requestDelay = 2000; // 2 seconds between requests

    public JustJoinItScraper(List<JobOffer> jobOffers, String keywords, String location,
                             List<String> offerLinks, Set<String> offerLinksSet, HelloApplication ui) {
        this.jobOffers = jobOffers;
        this.keywords = keywords;
        this.location = location;
        this.offerLinks = offerLinks;
        this.offerLinksSet = offerLinksSet;
        this.ui = ui;
        this.driverPool = new WebDriverPool(3); // Create pool with 3 drivers
    }

    public void startScraping() {
        scraperThread = new Thread(() -> {
            try {
                initializeDriver();
                if (!finishedScrolling.get()) {
                    scrollAndCollectLinks();
                }
                collectOfferLinks();
                closeDriver();
            } catch (Exception e) {
                System.err.println("[JustJoin.It] Error: " + e.getMessage());
                e.printStackTrace();
                finished.set(true);
                closeDriver();
            }
        });
        scraperThread.setDaemon(true);
        scraperThread.start();
    }

    private void initializeDriver() {
        try {
            System.out.println("[JustJoin.It] Setting up ChromeDriver for JustJoinIt...");
            WebDriverManager.chromedriver().setup();

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--no-sandbox");
            options.addArguments("--headless"); // Run in headless mode to reduce resource usage

            driver = new ChromeDriver(options);
            driverInitialized = true;
            System.out.println("[JustJoin.It] ChromeDriver initialized successfully.");

            String url = buildUrl();
            System.out.println("[JustJoin.It] Scraping: " + url);
            driver.get(url);
            waitForPageLoad();

            // Accept cookies if needed
            try {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                WebElement cookiesButton = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//*[@id='cookiescript_accept']")));
                cookiesButton.click();
                System.out.println("[JustJoin.It] Cookies accepted.");
            } catch (Exception e) {
                System.out.println("[JustJoin.It] Cookie button not found or not needed: " + e.getMessage());
            }

            waitForPageLoad();
            Thread.sleep(3000); // Initial waiting for page load
        } catch (Exception e) {
            System.err.println("[JustJoin.It] Error initializing ChromeDriver: " + e.getMessage());
            throw new RuntimeException("[JustJoin.It] Failed to initialize driver", e);
        }
    }

    private void scrollAndCollectLinks() {
        try {
            if (driver == null || !driverInitialized) {
                System.out.println("[JustJoin.It] Driver not initialized. Cannot scroll.");
                return;
            }

            JavascriptExecutor js = (JavascriptExecutor) driver;
            int previousLinkCount = 0;
            int noNewLinksCounter = 0;

            while (scrollCounter.get() < MAX_SCROLL_ATTEMPTS && !cancelled.get()) {
                // Collect links before scrolling
                collectOfferLinks();

                // Check if new links were found
                int currentLinkCount = offerLinksSet.size();
                if (currentLinkCount > previousLinkCount) {
                    previousLinkCount = currentLinkCount;
                    noNewLinksCounter = 0;
                } else {
                    noNewLinksCounter++;
                    if (noNewLinksCounter >= 3) {
                        System.out.println("[JustJoin.It] No new links found after 3 attempts, finishing...");
                        break;
                    }
                }

                // Scroll down
                js.executeScript("window.scrollBy(0, window.innerHeight);");
                Thread.sleep(SCROLL_WAIT_TIME);
            }

            finishedScrolling.set(true);
            System.out.println("[JustJoin.It] Finished scrolling. Total links collected: " + offerLinksSet.size());

        } catch (Exception e) {
            System.err.println("[JustJoin.It] Error during scrolling: " + e.getMessage());
            e.printStackTrace();
            finishedScrolling.set(true); // Ensure finished is set even on error
        } finally {
            finishedScrolling.set(true);
        }
    }

    private void collectOfferLinks() {
        try {
            List<WebElement> linkElements = driver.findElements(By.xpath("//a[contains(@href, '/job-offer/')]"));
            for (WebElement linkElement : linkElements) {
                String href = linkElement.getAttribute("href");
                if (href != null && href.contains("/job-offer/")) {
                    // Ensure href is a full URL
                    if (!href.startsWith("https://")) {
                        href = "https://justjoin.it" + href;
                    }

                    // Add to the set and list if it's a new link
                    boolean isNewLink = offerLinksSet.add(href);
                    if (isNewLink) {
                        synchronized (offerLinks) {
                            offerLinks.add(href);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[JustJoin.It] Error collecting links: " + e.getMessage());
        }
    }

    public void scrapeOfferDetails(String offerUrl) {
        try {
            Thread.sleep(requestDelay); // Opóźnienie między żądaniami

            if (cancelled.get()) {
                return;
            }

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--disable-dev-shm-usage");// dla stabilności
            options.addArguments("--no-sandbox");// dla stabilności
            options.addArguments("--headless");// zaoszczędzenie zasobów

            WebDriver offerDriver = new ChromeDriver(options);
            try {
                offerDriver.get(offerUrl);

                // Czekanie na załadowanie strony
                WebDriverWait wait = new WebDriverWait(offerDriver, Duration.ofSeconds(10));
                wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("/html/body/div[2]/div/div/div/div[2]/div[2]/div[1]/div[2]/div[2]/h1")));

                // Wyciągnięcie danych z HTML za pomocą XPath
                String title = getTextByXPath(offerDriver, "/html/body/div[2]/div/div/div/div[2]/div[2]/div[1]/div[2]/div[2]/h1", "No Data");
                String typeOfWork = getTextByXPath(offerDriver, "/html/body/div[2]/div/div/div/div[2]/div[2]/div[2]/div[1]/div[2]/div[2]", "No data");
                String experience = getTextByXPath(offerDriver, "/html/body/div[2]/div/div/div/div[2]/div[2]/div[2]/div[2]/div[2]/div[2]", "No data");
                String employmentType = getTextByXPath(offerDriver, "/html/body/div[2]/div/div/div/div[2]/div[2]/div[2]/div[3]/div[2]/div[2]", "No data");
                String operatingMode = getTextByXPath(offerDriver, "/html/body/div[2]/div/div/div/div[2]/div[2]/div[2]/div[4]/div[2]/div[2]", "No data");
                String location = getTextByXPath(offerDriver, "/html/body/div[2]/div/div/div/div[2]/div[2]/div[1]/div[2]/div[2]/div/div[2]/div/span", "No data");
                if ("No data".equals(location)) {
                    location = getTextByXPath(offerDriver, "/html/body/div[2]/div/div/div/div[2]/div[2]/div[1]/div[2]/div[2]/div/div[2]/button/div/span[1]", "No data") + getTextByXPath(offerDriver, "/html/body/div[2]/div/div/div/div[2]/div[2]/div[1]/div[2]/div[2]/div/div[2]/button/div/span[2]", "");
                }
                String company = getTextByXPath(offerDriver, "/html/body/div[2]/div/div/div/div[2]/div[2]/div[1]/div[2]/div[2]/div/div[1]", "No data");
                String salary = getTextByCss(offerDriver, "span.css-1tka0qn", "No data");

                // Dodanie oferty do listy
                synchronized (jobOffers) {
                    jobOffers.add(new JobOffer(title, company, salary, location, offerUrl, typeOfWork, experience, operatingMode));
                }

                System.out.println("[JustJoin.It] Scraped job details from JustJoinIt: " + title);
                ui.updateOffersCount(jobOffers.size());

            } finally {
                if (offerDriver != null) {
                    offerDriver.quit();
                }
            }

        } catch (Exception e) {
            System.err.println("[JustJoin.It] Error scraping JustJoinIt offer " + offerUrl + ": " + e.getMessage());
        }
    }

    // Pomocnicza metoda do pobierania tekstu z użyciem CSS
    private String getTextByCss(WebDriver driver, String cssSelector, String defaultValue) {
        try {
            WebElement element = driver.findElement(By.cssSelector(cssSelector));
            return element.getText().isEmpty() ? defaultValue : element.getText();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // Nowa pomocnicza metoda do pobierania tekstu z użyciem XPath
    private String getTextByXPath(WebDriver driver, String xpath, String defaultValue) {
        try {
            WebElement element = driver.findElement(By.xpath(xpath));
            return element.getText().isEmpty() ? defaultValue : element.getText();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String buildUrl() {
        StringBuilder url = new StringBuilder("https://justjoin.it/job-offers/");
        url.append(location.toLowerCase().replace(" ", "-"));
        if (!keywords.isEmpty()) {
            url.append("?keyword=").append(keywords.toLowerCase().replace(" ", "%20"));
        }
        return url.toString();
    }

    private void waitForPageLoad() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10)).until(
                    d -> ((JavascriptExecutor) d).executeScript("return document.readyState").equals("complete")
            );
            System.out.println("[JustJoin.It] Page loaded successfully.");
        } catch (Exception e) {
            System.out.println("[JustJoin.It]  Error waiting for page to load: " + e.getMessage());
        }
    }
    public boolean isFinishedCollectingLinks() {
        return finishedScrolling.get();
    }

    public void cancel() {
        cancelled.set(true);
        driverPool.shutdown();
    }

    private void closeDriver() {
        if (driver != null && driverInitialized) {
            try {
                driver.quit();
                driverInitialized = false;
                System.out.println("[JustJoin.It] ChromeDriver closed successfully.");
            } catch (Exception e) {
                System.err.println("[JustJoin.It] Error closing ChromeDriver: " + e.getMessage());
            }
        }
    }
}