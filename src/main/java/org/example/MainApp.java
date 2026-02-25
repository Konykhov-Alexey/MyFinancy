package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.example.config.HibernateUtil;
import org.example.util.DataSeeder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class MainApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    @Override
    public void init() {
        log.info("Инициализация приложения...");
        HibernateUtil.getSessionFactory();
        DataSeeder.seed();
    }

    @Override
    public void start(Stage stage) throws IOException {
        // Загружаем иконочный шрифт Font Awesome 6 Free Solid
        var fontStream = getClass().getResourceAsStream("/org/example/fonts/fa-solid-900.ttf");
        if (fontStream != null) {
            Font.loadFont(fontStream, 14);
            log.info("Font Awesome 6 загружен");
        } else {
            log.warn("Иконочный шрифт не найден");
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/fxml/main.fxml"));
        Scene scene = new Scene(loader.load(), 1200, 750);
        scene.getStylesheets().add(getClass().getResource("/org/example/css/styles.css").toExternalForm());

        stage.setTitle("MyFinancy");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();
    }

    @Override
    public void stop() {
        log.info("Завершение работы...");
        HibernateUtil.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
