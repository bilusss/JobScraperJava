package org.jobscraper.jobscraper;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class WebDriverPool {
    private final Queue<WebDriver> drivers;
    private final Semaphore semaphore;
    private static final int DEFAULT_POOL_SIZE = 3;
    private volatile boolean isShutdown = false;

    public WebDriverPool() {
        this(DEFAULT_POOL_SIZE);
    }

    public WebDriverPool(int poolSize) {
        this.drivers = new ConcurrentLinkedQueue<>();
        this.semaphore = new Semaphore(poolSize);
        initializePool(poolSize);
    }

    private void initializePool(int size) {
        WebDriverManager.chromedriver().setup();
        for (int i = 0; i < size; i++) {
            drivers.offer(createDriver());
        }
    }

    private WebDriver createDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--no-sandbox");
        options.addArguments("--headless");

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(30, TimeUnit.SECONDS);
        return driver;
    }

    public WebDriver borrowDriver() throws InterruptedException {
        if (isShutdown) {
            throw new IllegalStateException("Pool is shutdown");
        }
        semaphore.acquire();
        WebDriver driver = drivers.poll();
        if (driver == null) {
            driver = createDriver();
        }
        return driver;
    }

    public void returnDriver(WebDriver driver) {
        if (driver != null && !isShutdown) {
            drivers.offer(driver);
            semaphore.release();
        }
    }

    public void shutdown() {
        isShutdown = true;
        drivers.forEach(WebDriver::quit);
        drivers.clear();
    }
}