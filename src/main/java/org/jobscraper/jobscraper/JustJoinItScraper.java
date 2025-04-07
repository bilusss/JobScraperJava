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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private final Map<String, Boolean> processedLinks = new ConcurrentHashMap<>();

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
        Thread scraperThread = new Thread(() -> {
            try {
                initializeDriver();
                if (!finishedScrolling.get()){
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
        WebDriver driver = null;
        // Rate limiting
        try {
            driver = driverPool.borrowDriver();
            Thread.sleep(2000); // 2 seconds between requests

            if (cancelled.get()) {
                return;
            }

            // Use a separate WebDriver instance for each job detail page to avoid issues
            WebDriver offerDriver = null;
            try {
                ChromeOptions options = new ChromeOptions();
                options.addArguments("--disable-dev-shm-usage");
                options.addArguments("--no-sandbox");
                options.addArguments("--headless");

                offerDriver = new ChromeDriver(options);
                offerDriver.get(offerUrl);

                // Wait for page to load
                Thread.sleep(3000);

                // Extract job details
                String title = getTextByCss(offerDriver, "h1", "Brak tytułu");
                String company = getTextByCss(offerDriver, "h2", "Brak firmy");
                String salary = getTextByCss(offerDriver, "div.css-1b2ga3v", "Brak danych o zarobkach");
                String location = getTextByCss(offerDriver, "div.css-11ost19", "Brak lokalizacji");

                // Extract additional details from the offer
                String typeOfWork = getOfferDetail(offerDriver, "Type of work");
                String experience = getOfferDetail(offerDriver, "Experience");
                String operatingMode = getOfferDetail(offerDriver, "Operating mode");

                synchronized (jobOffers) {
                    jobOffers.add(new JobOffer(title, company, salary, location, offerUrl,
                            typeOfWork, experience, operatingMode));
                }

                System.out.println("[JustJoin.It] Scraped job details: " + title);
                if (jobOffers.size() == offerLinksSet.size()) {
                    finished.set(true);
                    ui.updateUI(jobOffers.size(), offerLinksSet.size(), null);
                    System.out.println("[JustJoin.It] All job offers scraped.");
                }
                ui.updateOffersCount(jobOffers.size());

            } finally {
                // Always close the driver
                if (offerDriver != null) {
                    try {
                        offerDriver.quit();
                    } catch (Exception e) {
                        System.err.println("[JustJoin.It] Error closing offer driver: " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("[JustJoin.It] Error scraping offer: " + e.getMessage());
        } finally {
            if (driver != null) {
                driverPool.returnDriver(driver);
            }
        }
    }

    private String getTextByCss(WebDriver driver, String cssSelector, String defaultValue) {
        try {
            WebElement element = driver.findElement(By.cssSelector(cssSelector));
            return element.getText();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String getOfferDetail(WebDriver driver, String label) {
        try {
            List<WebElement> elements = driver.findElements(By.cssSelector("div.css-1bpmjmb"));
            for (WebElement element : elements) {
                if (element.getText().contains(label)) {
                    WebElement valueElement = element.findElement(By.xpath("following-sibling::div"));
                    return valueElement.getText();
                }
            }
            return "Brak danych";
        } catch (Exception e) {
            return "Brak danych";
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

    public boolean isFinished() {
        return finished.get();
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