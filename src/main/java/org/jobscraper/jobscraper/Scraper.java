package org.jobscraper.jobscraper;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Scraper {
    private final List<JobOffer> jobOffers = new ArrayList<>(); // Lista ofert pracy dostępna dla wielu wątków
    private final List<String> offerLinks = new ArrayList<>(); // Lista linków do ofert dostępna dla wielu wątków
    private final Set<String> offerLinksSet = Collections.synchronizedSet(new HashSet<>()); // Thread-safe zbiór linków ofert
    private final Set<String> processedLinks = Collections.synchronizedSet(new HashSet<>()); // Thread-safe zbiór przetworzonych linków
    private final String keywords;
    private final String location;
    private String distance;
    private final boolean scrapePracuj;
    private final boolean scrapeJustJoinIt;
    private final AtomicBoolean justJoinItLinksCollected = new AtomicBoolean(false); // Thread-safe flaga informująca czy zakończono zbieranie linków z JustJoinIt
    private final AtomicBoolean pracujPlLinksCollected = new AtomicBoolean(false); // Thread-safe flaga informująca czy zakończono zbieranie linków z Pracuj.pl
    private final HelloApplication ui;
    private boolean finished = false;
    private AtomicBoolean isCancelled = new AtomicBoolean(false); // Thread-safe flaga do anulowania operacji
    private ExecutorService executor; // Pula wątków do równoległego przetwarzania linków

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

    // Metoda startScraping uruchamia nowy wątek do zarządzania procesem scrapowania
    public void startScraping() {
        Thread scraperThread = new Thread(() -> {
            // Kod uruchamiany w osobnym wątku
            try {
                // Użycie CopyOnWriteArrayList dla bezpieczeństwa wątków - kolekcja zoptymalizowana dla wielu czytelników i niewielu pisarzy
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
                    pracujPlScraper = new PracujPlScraper(jobOffers, keywords, location, distance, offerLinks, offerLinksSet, ui);
                    pracujPlScraper.startScraping();
                    // Set flag to false initially for PracujPl
                    pracujPlLinksCollected.set(false);
                } else {
                    // If not scraping PracujPl, mark as already completed
                    pracujPlLinksCollected.set(true);
                }

                // Tworzenie puli wątków do równoległego przetwarzania ofert pracy
                executor = Executors.newFixedThreadPool(5); // Tworzy pulę 5 wątków roboczych

                // Monitor the progress and process links
                JustJoinItScraper finalJustJoinItScraper = justJoinItScraper;
                PracujPlScraper finalPracujPlScraper = pracujPlScraper;
                System.out.println("Scraping details started...");

                // Monitorowanie postępu i przetwarzanie linków w pętli
                while (!isCancelled.get()) {
                    // Sprawdzanie flag atomowych w sposób thread-safe
                    if (scrapeJustJoinIt && finalJustJoinItScraper != null && finalJustJoinItScraper.isFinishedCollectingLinks()) {
                        justJoinItLinksCollected.set(true);
                    }

                    if (scrapePracuj && finalPracujPlScraper != null && finalPracujPlScraper.isFinishedCollectingLinks()) {
                        pracujPlLinksCollected.set(true);
                    }

                    // Zbieranie nieprzetworzonych linków w celu równoległego przetwarzania
                    List<String> unprocessedLinks = new ArrayList<>();
                    synchronized (offerLinks) { // Synchronizacja dostępu do współdzielonej listy
                        // Sprawdzanie i dodawanie nowych linków do przetworzenia
                        for (String link : offerLinks) {
                            if (!processedLinks.contains(link)) {
                                unprocessedLinks.add(link);
                                processedLinks.add(link);
                            }
                        }
                    }

                    // Równoległe przetwarzanie linków przy użyciu puli wątków
                    for (String link : unprocessedLinks) {
                        if (isCancelled.get()) break;

                        final String finalLink = link;
                        executor.submit(() -> { // Delegowanie zadania do puli wątków
                            // Przetwarzanie linku przez odpowiedni scraper
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

                    // Sprawdzanie czy wszystkie zadania zostały zakończone
                    if (justJoinItLinksCollected.get() && pracujPlLinksCollected.get() &&
                            processedLinks.size() >= offerLinksSet.size() &&
                            jobOffers.size() >= processedLinks.size()) {

                        // Bezpieczne zatrzymanie puli wątków
                        executor.shutdown();
                        boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS); // Oczekiwanie na zakończenie wszystkich zadań
                        if (terminated) {
                            // Informacja o zakończeniu i aktualizacja UI
                            System.out.println("Scraping details finished...");
                            ui.finishScraping(jobOffers.size());
                            ui.updateUI(jobOffers.size(), offerLinks.size(), true);
                            break; // Exit the loop once all tasks are done
                        } else {
                            // Wymuszenie zamknięcia puli wątków jeśli nie zakończyła pracy w określonym czasie
                            System.err.println("Executor did not terminate in time, forcing shutdown...");
                            executor.shutdownNow();
                        }
                    }

                    Thread.sleep(1000); // Mały delay aby uniknąć zbyt intensywnego sprawdzania warunku
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

                // Upewnienie się, że pula wątków jest zamknięta
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

        scraperThread.setDaemon(true); // Ustawienie wątku jako daemon - zakończy się gdy program główny się zakończy
        scraperThread.start(); // Uruchomienie wątku
    }

    public boolean isFinished() {
        return finished;
    }

    public List<JobOffer> getJobOffers() {
        return new ArrayList<>(jobOffers);
    }

    // Metoda do anulowania procesu scrapowania
    public void cancel() {
        isCancelled.set(true); // Thread-safe ustawienie flagi anulowania
        if (executor != null) {
            executor.shutdownNow(); // Natychmiastowe zatrzymanie wszystkich wątków w puli
        }
        ui.finishScraping(jobOffers.size());
    }
}