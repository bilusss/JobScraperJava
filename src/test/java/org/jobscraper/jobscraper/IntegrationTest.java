package org.jobscraper.jobscraper;

import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

@ExtendWith(ApplicationExtension.class)
public class IntegrationTest {

    private HelloApplication application;

    @Start
    public void start(Stage stage) {
        application = new HelloApplication();
        application.start(stage);
    }

    @Test
    void testFullScrapingFlow(FxRobot robot) throws InterruptedException {
        // Wypełnienie formularza
        robot.clickOn("#keywordsField").write("Java");
        robot.clickOn("#locationField").write("Warszawa");

        // Zaznaczenie stron do scrapowania
        robot.clickOn("#scrapePracujCheckBox");

        // Rozpoczęcie scrapowania
        robot.clickOn("#startButton");

        // Poczekanie na zakończenie scrapowania (maksymalnie 10 sekund)
        long timeout = System.currentTimeMillis() + 10000;
        boolean done = false;

        while (System.currentTimeMillis() < timeout && !done) {
            // Sprawdzenie, czy proces scrapowania się zakończył
            String timeRemainingText = ((Label) robot.lookup(".label").nth(5).query()).getText();
            if (timeRemainingText.contains("Zakończono")) {
                done = true;
            } else {
                TimeUnit.MILLISECONDS.sleep(500);
            }
        }

        // Anulowanie scrapowania, jeśli jeszcze trwa
        if (!done) {
            robot.clickOn("#cancelButton");
        }

        // Sprawdzenie, czy przyciski zostały odpowiednio zaktualizowane
        assertFalse(robot.lookup("#startButton").queryButton().isDisable());
        assertTrue(robot.lookup("#cancelButton").queryButton().isDisable());

        // Testowanie eksportu do CSV
        if (!robot.lookup("#exportButton").queryButton().isDisable()) {
            // W rzeczywistym teście musielibyśmy zamokować FileChooser
            // Tutaj pomijamy kliknięcie exportButton, które otworzyłoby okno dialogowe
        }
    }

    @Test
    void testCancelScraping(FxRobot robot) throws InterruptedException {
        // Wypełnienie formularza
        robot.clickOn("#keywordsField").write("Java");
        robot.clickOn("#locationField").write("Warszawa");

        // Zaznaczenie stron do scrapowania
        robot.clickOn("#scrapePracujCheckBox");
        robot.clickOn("#scrapeJustJoinItCheckBox");

        // Rozpoczęcie scrapowania
        robot.clickOn("#startButton");

        // Poczekanie chwilę, aby scraping się rozpoczął
        TimeUnit.SECONDS.sleep(1);

        // Anulowanie scrapowania
        robot.clickOn("#cancelButton");

        // Poczekanie na zakończenie anulowania
        TimeUnit.SECONDS.sleep(1);

        // Sprawdzenie, czy UI zostało zaktualizowane
        assertFalse(robot.lookup("#startButton").queryButton().isDisable());
        assertTrue(robot.lookup("#cancelButton").queryButton().isDisable());
    }
}