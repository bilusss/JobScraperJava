package org.jobscraper.jobscraper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JobOfferTest {

    @Test
    void testCreateJobOffer() {
        // Tworzenie obiektu JobOffer z pełnymi danymi
        JobOffer offer = new JobOffer(
                "Java Developer",
                "Example Company",
                "10000-15000 PLN",
                "Warszawa",
                "https://example.com/job/123",
                "Umowa o pracę",
                "Mid",
                "Hybrydowo"
        );

        // Sprawdzenie, czy wszystkie pola zostały poprawnie ustawione
        assertEquals("Java Developer", offer.getTitle());
        assertEquals("Example Company", offer.getCompany());
        assertEquals("10000-15000 PLN", offer.getSalary());
        assertEquals("Warszawa", offer.getLocation());
        assertEquals("https://example.com/job/123", offer.getUrl());
        assertEquals("Umowa o pracę", offer.getTypeOfWork());
        assertEquals("Mid", offer.getExperience());
        assertEquals("Hybrydowo", offer.getOperatingMode());
    }

    @Test
    void testEqualsAndHashCode() {
        // Tworzenie dwóch ofert z tymi samymi URL-ami
        JobOffer offer1 = new JobOffer(
                "Java Developer",
                "Example Company",
                "10000-15000 PLN",
                "Warszawa",
                "https://example.com/job/123",
                "Umowa o pracę",
                "Mid",
                "Hybrydowo"
        );

        JobOffer offer2 = new JobOffer(
                "Java Developer (zmieniony tytuł)",
                "Other Company",
                "12000-16000 PLN",
                "Kraków",
                "https://example.com/job/123", // ten sam URL
                "B2B",
                "Senior",
                "Zdalnie"
        );

        // Oferty powinny być równe, jeśli mają ten sam URL
        assertEquals(offer1, offer2);
        assertEquals(offer1.hashCode(), offer2.hashCode());

        // Tworzenie oferty z innym URL-em
        JobOffer offer3 = new JobOffer(
                "Java Developer",
                "Example Company",
                "10000-15000 PLN",
                "Warszawa",
                "https://example.com/job/456", // inny URL
                "Umowa o pracę",
                "Mid",
                "Hybrydowo"
        );

        // Oferty nie powinny być równe, jeśli mają różne URL-e
        assertNotEquals(offer1, offer3);
    }

    @Test
    void testToString() {
        JobOffer offer = new JobOffer(
                "Java Developer",
                "Example Company",
                "10000-15000 PLN",
                "Warszawa",
                "https://example.com/job/123",
                "Umowa o pracę",
                "Mid",
                "Hybrydowo"
        );

        String toString = offer.toString();

        // Sprawdzenie, czy toString zawiera wszystkie ważne informacje
        assertTrue(toString.contains("Java Developer"));
        assertTrue(toString.contains("Example Company"));
        assertTrue(toString.contains("10000-15000 PLN"));
        assertTrue(toString.contains("Warszawa"));
        assertTrue(toString.contains("https://example.com/job/123"));
    }
}