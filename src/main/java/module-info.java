module org.jobscraper.jobscraper {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.jsoup;         // Dla Jsoup
    requires com.opencsv;       // Dla OpenCSV

    opens org.jobscraper.jobscraper to javafx.fxml; // Potrzebne dla FXML
    exports org.jobscraper.jobscraper;              // Eksport pakietu
}