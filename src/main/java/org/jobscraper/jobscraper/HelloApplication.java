package org.jobscraper.jobscraper;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class HelloApplication extends Application {

    private TextField keywordsField;
    private TextField locationField;
    private TextField distanceField;
    private CheckBox itSubpageCheckBox;
    private Label offersCountLabel;
    private Label timeRemainingLabel;
    private Button startButton;
    private Button cancelButton;
    private Scraper scraper;

    @Override
    public void start(Stage stage) {
        // Tworzenie elementów GUI (bez zmian)
        Label keywordsLabel = new Label("Słowa kluczowe:");
        keywordsField = new TextField();
        keywordsField.setPromptText("np. Java Developer");

        Label locationLabel = new Label("Lokalizacja:");
        locationField = new TextField();
        locationField.setPromptText("np. Warszawa");

        Label distanceLabel = new Label("Odległość (km):");
        distanceField = new TextField();
        distanceField.setPromptText("np. 50");

        itSubpageCheckBox = new CheckBox("Podstrona IT (it.pracuj.pl)");

        startButton = new Button("Start");
        cancelButton = new Button("Cancel");
        cancelButton.setDisable(true);

        offersCountLabel = new Label("Liczba ofert: 0");
        timeRemainingLabel = new Label("Czas pozostały: --");

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        grid.add(keywordsLabel, 0, 0);
        grid.add(keywordsField, 1, 0);
        grid.add(locationLabel, 0, 1);
        grid.add(locationField, 1, 1);
        grid.add(distanceLabel, 0, 2);
        grid.add(distanceField, 1, 2);
        grid.add(itSubpageCheckBox, 0, 3, 2, 1);

        HBox buttonBox = new HBox(10, startButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER);
        grid.add(buttonBox, 0, 4, 2, 1);

        grid.add(offersCountLabel, 0, 5, 2, 1);
        grid.add(timeRemainingLabel, 0, 6, 2, 1);

        grid.setStyle("-fx-background-color: #f0f8ff;");
        startButton.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white;");
        cancelButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");

        Scene scene = new Scene(grid, 400, 300);
        stage.setTitle("JobScraper - Pracuj.pl");
        stage.setScene(scene);
        stage.show();

        // Event Listeners
        startButton.setOnAction(event -> startScraping());
        cancelButton.setOnAction(event -> cancelScraping());
    }

    private void startScraping() {
        startButton.setDisable(true);
        cancelButton.setDisable(false);
        offersCountLabel.setText("Liczba ofert: 0");
        timeRemainingLabel.setText("Czas pozostały: Trwa...");

        scraper = new Scraper(
                keywordsField.getText(),
                locationField.getText(),
                distanceField.getText(),
                itSubpageCheckBox.isSelected()
        );
        scraper.startScraping(this);
    }

    private void cancelScraping() {
        if (scraper != null) {
            scraper.cancel();
        }
        startButton.setDisable(false);
        cancelButton.setDisable(true);
        timeRemainingLabel.setText("Czas pozostały: Anulowano");
    }

    public void updateOffersCount(int count) {
        Platform.runLater(() -> offersCountLabel.setText("Liczba ofert: " + count));
    }

    public void finishScraping(int finalCount) {
        Platform.runLater(() -> {
            startButton.setDisable(false);
            cancelButton.setDisable(true);
            timeRemainingLabel.setText("Czas pozostały: Zakończono");
            offersCountLabel.setText("Liczba ofert: " + finalCount);
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}