package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    @FXML private StackPane contentArea;
    @FXML private Button btnDashboard;
    @FXML private Button btnTransactions;
    @FXML private Button btnCategories;
    @FXML private Button btnGoals;
    @FXML private Button btnBudget;
    @FXML private Button btnAnalytics;

    private List<Button> navButtons;

    @FXML
    public void initialize() {
        navButtons = List.of(btnDashboard, btnTransactions, btnCategories, btnGoals, btnBudget, btnAnalytics);
        showDashboard();
    }

    @FXML private void showDashboard()    { loadView("/org/example/fxml/dashboard.fxml",    btnDashboard); }
    @FXML private void showTransactions() { loadView("/org/example/fxml/transactions.fxml", btnTransactions); }
    @FXML private void showCategories()   { loadView("/org/example/fxml/categories.fxml",   btnCategories); }
    @FXML private void showGoals()        { loadView("/org/example/fxml/goals.fxml",        btnGoals); }
    @FXML private void showBudget()       { loadView("/org/example/fxml/budget.fxml",       btnBudget); }
    @FXML private void showAnalytics()    { loadView("/org/example/fxml/analytics.fxml",    btnAnalytics); }

    private void loadView(String fxmlPath, Button activeBtn) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node view = loader.load();
            contentArea.getChildren().setAll(view);
            navButtons.forEach(b -> b.getStyleClass().remove("nav-button-active"));
            activeBtn.getStyleClass().add("nav-button-active");
        } catch (IOException e) {
            log.error("Ошибка загрузки {}: {}", fxmlPath, e.getMessage(), e);
        }
    }
}
