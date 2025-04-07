module org.jobscraper.jobscraper {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.base;
    requires javafx.graphics;
    requires org.jsoup;
    requires com.opencsv;
    requires org.seleniumhq.selenium.api;
    requires org.seleniumhq.selenium.chrome_driver;
    requires org.seleniumhq.selenium.support;
    requires dev.failsafe.core;
    requires io.github.bonigarcia.webdrivermanager;
    requires com.google.gson;
    requires org.slf4j; // Dodaj dla SLF4J
    requires org.slf4j.simple; // Dodaj dla slf4j-simple

    opens org.jobscraper.jobscraper to javafx.fxml;
    exports org.jobscraper.jobscraper;
}