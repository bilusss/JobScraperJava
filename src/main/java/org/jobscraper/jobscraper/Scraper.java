package org.jobscraper.jobscraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Scraper {
    private final List<JobOffer> jobOffers = new ArrayList<>();
    private final List<String> offerLinks = new ArrayList<>();
    private final Set<String> offerLinksSet = Collections.synchronizedSet(new HashSet<>()); // Added as class field
    private final Set<String> processedLinks = Collections.synchronizedSet(new HashSet<>());
    private final String keywords;
    private final String location;
    private String distance;
    private final boolean scrapePracuj;
    private final boolean scrapeJustJoinIt;
    private final AtomicBoolean justJoinItLinksCollected = new AtomicBoolean(false);
    private final AtomicBoolean pracujPlLinksCollected = new AtomicBoolean(false);
    private final HelloApplication ui;
    private boolean finished = false;
    private int maxOffers = 100; // Default value
    private AtomicBoolean isCancelled = new AtomicBoolean(false);
    private AtomicBoolean initialScrapingDone = new AtomicBoolean(false);
    private ExecutorService executor;
    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:123.0) Gecko/20100101 Firefox/123.0"
    };

    public Scraper(String keywords, String location, String distance, boolean scrapePracuj,
                   boolean scrapeJustJoinIt, HelloApplication ui) {
        this.keywords = keywords;
        this.location = location;
        this.distance = distance;
        this.scrapePracuj = scrapePracuj;
        this.scrapeJustJoinIt = scrapeJustJoinIt;
        this.ui = ui;
    }

    private String getRandomUserAgent() {
        int index = (int) (Math.random() * USER_AGENTS.length);
        return USER_AGENTS[index];
    }

    public void startScraping() {
        Thread scraperThread = new Thread(() -> {
            try {
                // Use CopyOnWriteArrayList for thread safety
                List<JobOffer> jobOffers = new CopyOnWriteArrayList<>();

                // Create and start the individual scrapers
                JustJoinItScraper justJoinItScraper = null;
                PracujPlScraper pracujPlScraper = null;

                if (scrapeJustJoinIt) {
                    justJoinItScraper = new JustJoinItScraper(jobOffers, keywords, location, offerLinks, offerLinksSet, ui);
                    justJoinItScraper.startScraping();
                    // Set flag to false initially for JustJoinIt
                    justJoinItLinksCollected.set(false);
                } else {
                    // If not scraping JustJoinIt, mark as already completed
                    justJoinItLinksCollected.set(true);
                }

                if (scrapePracuj) {
                    pracujPlScraper = new PracujPlScraper(jobOffers, keywords, location, offerLinks, offerLinksSet, ui);
                    pracujPlScraper.startScraping();
                    // Set flag to false initially for PracujPl
                    pracujPlLinksCollected.set(false);
                } else {
                    // If not scraping PracujPl, mark as already completed
                    pracujPlLinksCollected.set(true);
                }

                // Create thread pool for processing offers
                executor = Executors.newFixedThreadPool(5); // Adjust the number of threads as needed

                // Monitor the progress and process links
                JustJoinItScraper finalJustJoinItScraper = justJoinItScraper;
                PracujPlScraper finalPracujPlScraper = pracujPlScraper;
                System.out.println("Scraping details started...");

                while (!isCancelled.get()) {
                    // Check if both scrapers have finished collecting links
                    if (scrapeJustJoinIt && finalJustJoinItScraper != null && finalJustJoinItScraper.isFinishedCollectingLinks()) {
                        justJoinItLinksCollected.set(true);
                    }

                    if (scrapePracuj && finalPracujPlScraper != null && finalPracujPlScraper.isFinishedCollectingLinks()) {
                        pracujPlLinksCollected.set(true);
                    }

                    boolean justJoinItDone = finalJustJoinItScraper == null || finalJustJoinItScraper.isFinished();
                    boolean pracujPlDone = finalPracujPlScraper == null || finalPracujPlScraper.isFinished();

                    // Process new links in batches
                    List<String> unprocessedLinks = new ArrayList<>();
                    synchronized (offerLinks) {
                        for (String link : offerLinks) {
                            if (!processedLinks.contains(link)) {
                                unprocessedLinks.add(link);
                                processedLinks.add(link);
                            }
                        }
                    }

                    // Submit tasks to process the links TODO
                    for (String link : unprocessedLinks) {
                        if (isCancelled.get()) break;

                        final String finalLink = link;
                        executor.submit(() -> {
                            if (finalLink.contains("justjoin.it") && finalJustJoinItScraper != null) {

                                finalJustJoinItScraper.scrapeOfferDetails(finalLink);
                            } else if (finalLink.contains("pracuj.pl") && finalPracujPlScraper != null) {
                                finalPracujPlScraper.scrapeOfferDetails(finalLink);
                            }
                        });
                    }

                    // Update UI with current progress
                    ui.updateUI(jobOffers.size(), offerLinksSet.size(), null);
                    ui.updateLinksCount(offerLinksSet.size());

                    // Check if both scrapers have finished collecting links and all collected links have been processed
                    if (justJoinItLinksCollected.get() && pracujPlLinksCollected.get() &&
                            processedLinks.size() >= offerLinksSet.size() &&
                            jobOffers.size() >= processedLinks.size()) {

                        // Wait for all tasks in executor to complete
                        executor.shutdown();
                        boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
                        if (terminated) {
                            System.out.println("Scraping details finished...");
                            ui.finishScraping(jobOffers.size());
                            ui.updateUI(jobOffers.size(), offerLinks.size(), true);
                            break; // Exit the loop once all tasks are done
                        } else {
                            System.err.println("Executor did not terminate in time, forcing shutdown...");
                            executor.shutdownNow();
                        }
                    }

                    Thread.sleep(1000); // Small delay to avoid busy-waiting
                }

                // Cancel scrapers if they're still running (in case of cancellation)
                if (finalJustJoinItScraper != null) {
                    System.out.println("Cancelling JustJoinIt scraper...");
                    finalJustJoinItScraper.cancel();
                }
                if (finalPracujPlScraper != null) {
                    finalPracujPlScraper.cancel();
                    System.out.println("Cancelling PracujPl scraper...");
                }

                // Ensure executor is shut down
                if (executor != null && !executor.isShutdown()) {
                    executor.shutdownNow();
                }

                // Final UI update
                ui.updateUI(jobOffers.size(), offerLinks.size(), true);

                // Copy results to main lists
                synchronized (this.jobOffers) {
                    this.jobOffers.clear();
                    this.jobOffers.addAll(jobOffers);
                }

                finished = true;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Scraping interrupted: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Error in startScraping: " + e.getMessage());
                e.printStackTrace();
            }
        });

        scraperThread.setDaemon(true);
        scraperThread.start();
    }

    private synchronized void checkIfFinished() {
        boolean pracujDone = !scrapePracuj || new PracujPlScraper(jobOffers, keywords, location, offerLinks, offerLinksSet, ui).isFinished();
        boolean justJoinItDone = !scrapeJustJoinIt || new JustJoinItScraper(jobOffers, keywords, location, offerLinks, offerLinksSet, ui).isFinished();

        finished = pracujDone && justJoinItDone;

        if (finished && !isCancelled.get()) {
            ui.finishScraping(jobOffers.size(), offerLinksSet.size());
        }
    }

    public boolean isFinished() {
        return finished;
    }

    public List<JobOffer> getJobOffers() {
        return new ArrayList<>(jobOffers);
    }

    public void cancel() {
        isCancelled.set(true);
        if (executor != null) {
            executor.shutdownNow();
        }
        ui.finishScraping(jobOffers.size());
    }
}