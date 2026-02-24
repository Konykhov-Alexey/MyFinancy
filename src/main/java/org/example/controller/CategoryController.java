package org.example.controller;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.example.model.entity.Category;
import org.example.model.enums.CategoryType;
import org.example.repository.CategoryRepository;
import org.example.service.CategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public class CategoryController {

    private static final Logger log = LoggerFactory.getLogger(CategoryController.class);

    @FXML private VBox incomeList;
    @FXML private VBox expenseList;
    @FXML private TextField incomeNameField;
    @FXML private TextField expenseNameField;

    private CategoryService service;

    @FXML
    public void initialize() {
        service = new CategoryService(new CategoryRepository());
        refresh();
    }

    @FXML
    private void addIncomeCategory() {
        addCategory(incomeNameField, CategoryType.INCOME);
    }

    @FXML
    private void addExpenseCategory() {
        addCategory(expenseNameField, CategoryType.EXPENSE);
    }

    private void addCategory(TextField field, CategoryType type) {
        String name = field.getText().trim();
        if (name.isEmpty()) return;
        service.add(name, type);
        field.clear();
        refresh();
    }

    private void refresh() {
        buildColumn(incomeList, service.getByType(CategoryType.INCOME), false);
        buildColumn(expenseList, service.getByType(CategoryType.EXPENSE), true);
    }

    private void buildColumn(VBox container, List<Category> categories, boolean withBudget) {
        container.getChildren().clear();
        for (Category cat : categories) {
            container.getChildren().add(buildRow(cat, withBudget));
        }
    }

    private HBox buildRow(Category category, boolean withBudget) {
        HBox row = new HBox(8);
        row.getStyleClass().add("category-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(category.getName());
        nameLabel.getStyleClass().add("category-name");
        HBox.setHgrow(nameLabel, Priority.ALWAYS);
        row.getChildren().add(nameLabel);

        if (withBudget) {
            row.getChildren().add(buildLimitField(category));
            if (category.getMonthlyLimit() != null && category.getMonthlyLimit().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal spent = service.getSpentThisMonth(category);
                row.getChildren().addAll(
                        buildBudgetBar(spent, category.getMonthlyLimit()),
                        buildSpentLabel(spent, category.getMonthlyLimit())
                );
            }
        }

        if (!category.isDefault()) {
            Button del = new Button("✕");
            del.getStyleClass().add("btn-icon-danger");
            del.setOnAction(e -> {
                service.delete(category);
                refresh();
            });
            row.getChildren().add(del);
        }

        return row;
    }

    private TextField buildLimitField(Category category) {
        TextField field = new TextField();
        field.getStyleClass().add("limit-field");
        field.setPromptText("Лимит ₽");
        field.setTooltip(new Tooltip("Месячный лимит расходов. Enter — сохранить."));
        if (category.getMonthlyLimit() != null) {
            field.setText(category.getMonthlyLimit().stripTrailingZeros().toPlainString());
        }
        field.setOnAction(e -> applyLimit(category, field.getText()));
        field.focusedProperty().addListener((obs, old, focused) -> {
            if (!focused) applyLimit(category, field.getText());
        });
        return field;
    }

    private void applyLimit(Category category, String text) {
        BigDecimal newLimit = null;
        if (!text.isBlank()) {
            try {
                newLimit = new BigDecimal(text.replace(",", ".").trim());
                if (newLimit.compareTo(BigDecimal.ZERO) <= 0) newLimit = null;
            } catch (NumberFormatException e) {
                return;
            }
        }
        if (!Objects.equals(category.getMonthlyLimit(), newLimit)) {
            service.updateLimit(category, newLimit);
            refresh();
        }
    }

    private ProgressBar buildBudgetBar(BigDecimal spent, BigDecimal limit) {
        double ratio = spent.divide(limit, 4, RoundingMode.HALF_UP).doubleValue();
        ProgressBar bar = new ProgressBar(Math.min(ratio, 1.0));
        bar.getStyleClass().add("budget-bar");
        if (ratio > 1.0) {
            bar.getStyleClass().add("budget-bar-over");
        } else if (ratio >= 0.8) {
            bar.getStyleClass().add("budget-bar-warn");
        } else {
            bar.getStyleClass().add("budget-bar-ok");
        }
        return bar;
    }

    private Label buildSpentLabel(BigDecimal spent, BigDecimal limit) {
        Label label = new Label(String.format("%.0f / %.0f ₽", spent, limit));
        label.getStyleClass().add("budget-spent");
        return label;
    }
}
