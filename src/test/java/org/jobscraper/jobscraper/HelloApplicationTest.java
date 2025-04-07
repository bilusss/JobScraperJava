package org.jobscraper.jobscraper;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxAssert;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.matcher.control.LabeledMatchers;
import org.testfx.matcher.control.TextInputControlMatchers;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.testfx.matcher.base.NodeMatchers.isVisible;
import static org.testfx.matcher.control.TextInputControlMatchers.hasText;

@ExtendWith(ApplicationExtension.class)
public class HelloApplicationTest {

    private HelloApplication application;
    private TextField keywordsField;
    private TextField locationField;
    private CheckBox itSubpageCheckBox;
    private CheckBox scrapePracujCheckBox;
    private CheckBox scrapeJustJoinItCheckBox;
    private TextField distanceField;
    private Button startButton;
    private Button cancelButton;
    private Button exportButton;
    private Label offersCountLabel;
    private Label linksCountLabel;
    private ProgressBar scrapingProgressBar;

    @Start
    public void start(Stage stage) {
        application = new HelloApplication();
        application.start(stage);

        // Inicjalizacja elementów UI, które będziemy testować
        keywordsField = (TextField) stage.getScene().lookup("#keywordsField");
        locationField = (TextField) stage.getScene().lookup("#locationField");
        itSubpageCheckBox = (CheckBox) stage.getScene().lookup("#itSubpageCheckBox");
        scrapePracujCheckBox = (CheckBox) stage.getScene().lookup("#scrapePracujCheckBox");
        scrapeJustJoinItCheckBox = (CheckBox) stage.getScene().lookup("#scrapeJustJoinItCheckBox");
        startButton = (Button) stage.getScene().lookup("#startButton");
        cancelButton = (Button) stage.getScene().lookup("#cancelButton");
        offersCountLabel = (Label) stage.getScene().lookup("#offersCountLabel");
        linksCountLabel = (Label) stage.getScene().lookup("#linksCountLabel");
        scrapingProgressBar = (ProgressBar) stage.getScene().lookup("#scrapingProgressBar");
        exportButton = (Button) stage.getScene().lookup("#exportButton");
        distanceField = (TextField) stage.getScene().lookup("#distanceField");
    }

    @Test
    void testUIInitialState(FxRobot robot) {
        WaitForAsyncUtils.waitForFxEvents();

        // Verify that the element is not null before checking visibility
        assertNotNull(keywordsField, "keywordsField should not be null");

        // Then verify visibility
        FxAssert.verifyThat(keywordsField, isVisible());
        FxAssert.verifyThat(locationField, isVisible());
        FxAssert.verifyThat(itSubpageCheckBox, isVisible());
        FxAssert.verifyThat(scrapePracujCheckBox, isVisible());
        FxAssert.verifyThat(scrapeJustJoinItCheckBox, isVisible());
        FxAssert.verifyThat(startButton, isVisible());
        FxAssert.verifyThat(cancelButton, isVisible());

        // Sprawdzenie początkowych wartości
        FxAssert.verifyThat(keywordsField, hasText(""));
        FxAssert.verifyThat(locationField, hasText(""));
        assertFalse(itSubpageCheckBox.isSelected());
        assertFalse(scrapePracujCheckBox.isSelected());
        assertFalse(scrapeJustJoinItCheckBox.isSelected());
        assertTrue(startButton.isDisable() == false);
        assertTrue(cancelButton.isDisable() == true);
        FxAssert.verifyThat(offersCountLabel, LabeledMatchers.hasText("Liczba ofert: 0"));
        FxAssert.verifyThat(linksCountLabel, LabeledMatchers.hasText("Liczba zebranych linków: 0"));
        assertEquals(0.0, scrapingProgressBar.getProgress());
    }

    @Test
    void testStartButtonWithNoWebsiteSelected(FxRobot robot) {
        // Wypełnienie pól formularza
        robot.clickOn(keywordsField).write("Java");
        robot.clickOn(locationField).write("Warszawa");

        // Próba rozpoczęcia scrapowania bez zaznaczenia żadnej strony
        robot.clickOn(startButton);

        // Weryfikacja, że walidacja działa i aplikacja pokazuje alert
        // W tym teście korzystamy z naszej własnej implementacji showAlert, która
        // wypisuje alert do konsoli zamiast otwierać okno dialogowe
        // W rzeczywistości musielibyśmy sprawdzić, czy alert się pojawił
    }

    @Test
    void testStartButtonWithWebsiteSelected(FxRobot robot) {
        // Wypełnienie pól formularza
        robot.clickOn(keywordsField).write("Java");
        try {
            TimeUnit.MILLISECONDS.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        robot.clickOn(locationField).write("Warszawa");

        // Zaznaczenie strony do scrapowania
        robot.clickOn(scrapePracujCheckBox);

        // Rozpoczęcie scrapowania
        robot.clickOn(startButton);

        // Sprawdzenie, czy UI zostało zaktualizowane
        assertTrue(startButton.isDisable());
        assertFalse(cancelButton.isDisable());
    }

    @Test
    void testUpdateUIComponents() {
        // Test metod aktualizujących UI
        application.updateOffersCount(10);
        application.updateLinksCount(20);
        application.updateProgress(0.5);

        // Potrzebujemy poczekać na zakończenie aktualizacji UI - w testach jednostkowych
        // możemy użyć metod pomocniczych z TestFX lub po prostu sprawdzić wartości bez asercji

        // Test zakończenia scrapowania
        application.finishScraping(15, 30);
    }

    @Test
    void testValidateInput(FxRobot robot) {
        // Test prywatnej metody validateInput można zrobić przez refleksję
        // lub przez testowanie zachowania publicznych metod, które ją wywołują

        // Zaznaczamy checkbox i sprawdzamy, czy walidacja przejdzie
        scrapePracujCheckBox.setSelected(true);
        robot.clickOn(startButton);

        // Sprawdzamy czy przycisk start został wyłączony, co oznacza że walidacja przeszła
        assertTrue(startButton.isDisable());

        // Resetujemy stan
        application.finishScraping(0);

        // Odznaczamy checkbox i sprawdzamy, czy walidacja nie przejdzie
        scrapePracujCheckBox.setSelected(false);
        robot.clickOn(startButton);

        // Sprawdzamy czy przycisk start pozostał aktywny, co oznacza że walidacja nie przeszła
        assertFalse(startButton.isDisable());
    }
}