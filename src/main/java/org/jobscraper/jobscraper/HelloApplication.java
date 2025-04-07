package org.jobscraper.jobscraper;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class HelloApplication extends Application {
    private TextField keywordsField;
    private TextField locationField;
    private TextField distanceField;
    private CheckBox scrapePracujCheckBox;
    private CheckBox scrapeJustJoinItCheckBox;
    private Label offersCountLabel;
    private Label linksCountLabel;
    private Label timeRemainingLabel;
    private Button startButton;
    private Button cancelButton;
    private Button exportButton;
    private ProgressBar scrapingProgressBar;
    private Scraper scraper;
    private Stage primaryStage;
    private Boolean finished = true;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        // Initialize all controls
        Label keywordsLabel = new Label("Słowa kluczowe:");
        keywordsLabel.setId("keywordsLabel");
        keywordsField = new TextField();
        keywordsField.setId("keywordsField");
        keywordsField.setPromptText("np. Java");

        Label locationLabel = new Label("Lokalizacja:");
        locationLabel.setId("locationLabel");
        locationField = new TextField();
        locationField.setId("locationField");
        locationField.setPromptText("np. Kraków");

        Label distanceLabel = new Label("Odległość (km, tylko pracuj.pl):");
        distanceLabel.setId("distanceLabel");
        distanceField = new TextField();
        distanceField.setId("distanceField");
        distanceField.setPromptText("np. 10");

        Label scrapeLabel = new Label("Wybierz strony do scrapowania:");
        scrapeLabel.setId("scrapeLabel");
        scrapePracujCheckBox = new CheckBox("Pracuj.pl");
        scrapePracujCheckBox.setId("scrapePracujCheckBox");
        scrapeJustJoinItCheckBox = new CheckBox("JustJoin.it");
        scrapeJustJoinItCheckBox.setId("scrapeJustJoinItCheckBox");

        startButton = new Button("Start");
        startButton.setId("startButton");
        cancelButton = new Button("Cancel");
        cancelButton.setId("cancelButton");
        exportButton = new Button("Export CSV");
        cancelButton.setId("exportButton");

        cancelButton.setDisable(true);
        exportButton.setDisable(true);

        offersCountLabel = new Label("Liczba ofert: 0");
        offersCountLabel.setId("offersCountLabel");
        linksCountLabel = new Label("Liczba zebranych linków: 0");
        linksCountLabel.setId("linksCountLabel");
        timeRemainingLabel = new Label("Status: Gotowy");
        timeRemainingLabel.setId("timeRemainingLabel");

        scrapingProgressBar = new ProgressBar(0);
        scrapingProgressBar.setId("scrapingProgressBar");
        scrapingProgressBar.setMaxWidth(Double.MAX_VALUE);

        // Create layout
        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(10));

        // Add controls to grid
        gridPane.add(keywordsLabel, 0, 0);
        gridPane.add(keywordsField, 1, 0);
        gridPane.add(locationLabel, 0, 1);
        gridPane.add(locationField, 1, 1);
        gridPane.add(distanceLabel, 0, 2);
        gridPane.add(distanceField, 1, 2);
        gridPane.add(scrapeLabel, 0, 3);
        gridPane.add(scrapePracujCheckBox, 1, 3);
        gridPane.add(scrapeJustJoinItCheckBox, 1, 4);

        // Create button container
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().addAll(startButton, cancelButton, exportButton);

        // Create status container
        VBox statusBox = new VBox(5);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusBox.getChildren().addAll(offersCountLabel, linksCountLabel, timeRemainingLabel, scrapingProgressBar);

        // Main container
        VBox mainContainer = new VBox(10);
        mainContainer.setPadding(new Insets(10));
        mainContainer.getChildren().addAll(gridPane, buttonBox, statusBox);

        // Set up scene
        Scene scene = new Scene(mainContainer);
        stage.setTitle("Job Scraper");
        stage.setScene(scene);
        stage.show();

        // Button event handlers
        startButton.setOnAction(e -> startScraping());
        cancelButton.setOnAction(e -> cancelScraping());
        exportButton.setOnAction(e -> exportToCsv());
    }

    private void startScraping() {
        if (!validateInput()) {
            showAlert("Validation Error", "Please select at least one website to scrape.");
            return;
        }
        finished = false;
        startButton.setDisable(true);
        cancelButton.setDisable(false);
        exportButton.setDisable(true);
        timeRemainingLabel.setText("Status: Scraping...");

        scraper = new Scraper(
                replacePolishLetters(keywordsField.getText()).toLowerCase(),
                replacePolishLetters(locationField.getText()).toLowerCase(),
                distanceGetText(),
                scrapePracujCheckBox.isSelected(),
                scrapeJustJoinItCheckBox.isSelected(),
                this
        );
        scraper.startScraping();
    }
    private String replacePolishLetters(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("ą", "a")
                   .replace("ć", "c")
                   .replace("ę", "e")
                   .replace("ł", "l")
                   .replace("ń", "n")
                   .replace("ó", "o")
                   .replace("ś", "s")
                   .replace("ź", "z")
                   .replace("ż", "z")
                   .replace("Ą", "A")
                   .replace("Ć", "C")
                   .replace("Ę", "E")
                   .replace("Ł", "L")
                   .replace("Ń", "N")
                   .replace("Ó", "O")
                   .replace("Ś", "S")
                   .replace("Ź", "Z")
                   .replace("Ż", "Z");
    }

    private String distanceGetText() {
        String distance = distanceField.getText();
        if (distance.isEmpty()) {
            return "0"; // Default value
        }
        return distance;
    }

    private boolean validateInput() {
        return scrapePracujCheckBox.isSelected() || scrapeJustJoinItCheckBox.isSelected();
    }

    private void cancelScraping() {
        if (scraper != null) {
            scraper.cancel();
        }
    }

    private void exportToCsv() {
        if (scraper == null || scraper.getJobOffers().isEmpty()) {
            showAlert("Export Error", "No job offers to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save CSV File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );

        File file = fileChooser.showSaveDialog(primaryStage);
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                // Write CSV header
                writer.write("Title,Company,Salary,Location,Type of Work,Experience,Operating Mode,URL\n");

                // Write job offers
                for (JobOffer offer : scraper.getJobOffers()) {
                    writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                            escapeCsv(offer.getTitle()),
                            escapeCsv(offer.getCompany()),
                            escapeCsv(offer.getSalary()),
                            escapeCsv(offer.getLocation()),
                            escapeCsv(offer.getTypeOfWork()),
                            escapeCsv(offer.getExperience()),
                            escapeCsv(offer.getOperatingMode()),
                            escapeCsv(offer.getUrl())
                    ));
                }
                showAlert("Success", "Data exported successfully.");
            } catch (IOException e) {
                showAlert("Export Error", "Failed to export data: " + e.getMessage());
            }
        }
    }

    private String escapeCsv(String field) {
        return field.replace("\"", "\"\"");
    }
    public void updateUI(int jobOffersSize, int offerLinksSize, Boolean finishedTemp) {
        if (finishedTemp != null) {
            finished = finishedTemp;
        }

        if (jobOffersSize == offerLinksSize && offerLinksSize!=0 && finished) {
            finishScraping(jobOffersSize);
            return;
        }
//        System.out.println("FINISHED: " + finished);
        updateProgress(jobOffersSize, offerLinksSize);
        updateOffersCount(jobOffersSize);
        updateLinksCount(offerLinksSize);
    }

    public void updateOffersCount(int count) {
        Platform.runLater(() -> offersCountLabel.setText("Liczba ofert: " + count));
    }

    public void updateLinksCount(int count) {
        Platform.runLater(() -> linksCountLabel.setText("Liczba zebranych linków: " + count));
    }

    public void updateProgress(int jobOffersSize, int offerLinksSize) {
        Platform.runLater(() -> {
            scrapingProgressBar.setProgress((double) jobOffersSize / offerLinksSize);
            timeRemainingLabel.setText("Status: Scraping... " + jobOffersSize + " z " + offerLinksSize + " ofert.");
        });
    }

    public void updateProgress(double progress) {
        Platform.runLater(() -> scrapingProgressBar.setProgress(progress));
    }

    public void finishScraping(int offersCount) {
        Platform.runLater(() -> {
            startButton.setDisable(false);
            cancelButton.setDisable(true);
            exportButton.setDisable(false);
            timeRemainingLabel.setText("Status: Zakończono. Wyscrapowano " + offersCount + " ofert.");
            scrapingProgressBar.setProgress(1.0);
        });
    }

    public void finishScraping(int offersCount, int linksCount) {
        Platform.runLater(() -> {
            startButton.setDisable(false);
            cancelButton.setDisable(true);
            exportButton.setDisable(false);
            timeRemainingLabel.setText("Status: Zakończono. Znaleziono " + offersCount + " ofert z " + linksCount + " linków.");
            scrapingProgressBar.setProgress(1.0);
        });
    }

    private void showAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    @Override
    public void stop() {
        if (scraper != null) {
            scraper.cancel();
        }
    }

    public static void main(String[] args) {
        launch();
    }
}