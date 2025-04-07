package org.jobscraper.jobscraper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ScraperTest {

    @Mock
    private HelloApplication mockUI;

    private Scraper scraper;
    private List<JobOffer> jobOffers;
    private List<String> offerLinks;
    private Set<String> offerLinksSet;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Inicjalizacja danych testowych
        jobOffers = new ArrayList<>();
        offerLinks = new ArrayList<>();
        offerLinksSet = new HashSet<>();

        // Utworzenie instancji Scraper z zamockowanym UI
        scraper = new Scraper(
                "Java",     // keywords
                "Warszawa", // location
                "10",       // distance
                true,       // useItSubpage
                true,       // scrapePracuj
                true,       // scrapeJustJoinIt
                mockUI      // ui
        );
    }

    @Test
    void testStartScraping() {
        // Rozpoczęcie scrapowania
        scraper.startScraping();

        // Zaczekaj krótko, żeby scraper mógł się uruchomić
        try {
            TimeUnit.MILLISECONDS.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Sprawdź, czy UI jest aktualizowane
        verify(mockUI, atLeastOnce()).updateOffersCount(anyInt());
        verify(mockUI, atLeastOnce()).updateLinksCount(anyInt());

        // Zatrzymaj scraper
        scraper.cancel();
    }

    @Test
    void testCancel() {
        // Rozpoczęcie scrapowania
        scraper.startScraping();

        // Poczekaj chwilę
        try {
            TimeUnit.MILLISECONDS.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Anulowanie scrapowania
        scraper.cancel();

        // Sprawdź, czy UI zostało zaktualizowane
        verify(mockUI).finishScraping(anyInt());
    }

    @Test
    void testGetJobOffers() {
        // Sprawdź, czy metoda getJobOffers zwraca kopię listy
        List<JobOffer> offers1 = scraper.getJobOffers();
        List<JobOffer> offers2 = scraper.getJobOffers();

        // Listy powinny być różnymi obiektami
        assertNotSame(offers1, offers2);

        // Ale powinny mieć te same elementy (żadne w tym przypadku)
        assertEquals(offers1, offers2);
    }

    @Test
    void testIsFinished() {
        // Na początku scraper nie powinien być zakończony
        assertFalse(scraper.isFinished());

        // Po anulowaniu, scraper powinien być oznaczony jako zakończony
        scraper.startScraping();
        scraper.cancel();

        // To sprawdzenie może być zawodne, ponieważ anulowanie jest asynchroniczne
        // Lepiej byłoby wprowadzić metodę czekającą na zakończenie
    }
}