package org.example.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.example.model.entity.BudgetGroup;
import org.example.model.entity.Category;
import org.example.model.enums.CategoryType;
import org.example.repository.BudgetGroupRepository;
import org.example.repository.CategoryRepository;
import org.example.service.BudgetService;
import org.example.service.BudgetService.GroupBudget;
import org.example.service.BudgetService.MonthlyPlan;
import org.example.util.CurrencyFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class BudgetController {

    private static final Logger log = LoggerFactory.getLogger(BudgetController.class);

    // ── Preset colors для выбора группы ────────────────────────────
    private static final List<String> PRESET_COLORS = List.of(
            "#FF2D55", "#FF6B35", "#FFB800", "#22C55E",
            "#3B82F6", "#A855F7", "#EC4899", "#64748B"
    );

    // ── FXML nodes ───────────────────────────────────────────────────
    @FXML private ComboBox<YearMonth> monthCombo;
    @FXML private Label incomeValue;
    @FXML private Label unallocatedValue;
    @FXML private Label allocatedPctValue;
    @FXML private Label groupsCountValue;
    @FXML private ProgressBar allocationProgress;
    @FXML private Label allocationLabel;
    @FXML private Label allocationWarning;
    @FXML private VBox groupsBox;
    @FXML private VBox categoriesBox;

    private BudgetService service;

    // ── Lifecycle ────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        service = new BudgetService(new BudgetGroupRepository(), new CategoryRepository());

        // Заполнить ComboBox последними 12 месяцами
        YearMonth current = YearMonth.now();
        List<YearMonth> months = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            months.add(current.minusMonths(i));
        }
        monthCombo.setItems(FXCollections.observableArrayList(months));
        monthCombo.setValue(current);
        monthCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(YearMonth ym) {
                if (ym == null) return "";
                String m = ym.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru", "RU"));
                return capitalize(m) + " " + ym.getYear();
            }
            @Override
            public YearMonth fromString(String s) { return null; }
        });
        monthCombo.setOnAction(e -> refresh());

        refresh();
    }

    @FXML
    public void refresh() {
        YearMonth ym = monthCombo.getValue();
        if (ym == null) ym = YearMonth.now();

        MonthlyPlan plan = service.getMonthlyPlan(ym);
        BigDecimal totalPct = service.getTotalPercentage();

        updateMetrics(plan, totalPct);
        updateAllocationBar(totalPct);
        updateGroups(plan);
        updateCategories();
    }

    // ── Metrics ──────────────────────────────────────────────────────

    private void updateMetrics(MonthlyPlan plan, BigDecimal totalPct) {
        incomeValue.setText(CurrencyFormatter.format(plan.income()));

        BigDecimal unalloc = plan.unallocated();
        unallocatedValue.setText(CurrencyFormatter.format(unalloc));
        unallocatedValue.getStyleClass().removeAll("dash-positive", "dash-negative");
        unallocatedValue.getStyleClass().add(
                unalloc.compareTo(BigDecimal.ZERO) >= 0 ? "dash-positive" : "dash-negative");

        allocatedPctValue.setText(totalPct.setScale(0, RoundingMode.HALF_UP) + "%");
        groupsCountValue.setText(String.valueOf(plan.groups().size()));
    }

    // ── Allocation bar ───────────────────────────────────────────────

    private void updateAllocationBar(BigDecimal totalPct) {
        double pctDouble = totalPct.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                .doubleValue();
        allocationProgress.setProgress(Math.min(pctDouble, 1.0));

        allocationProgress.getStyleClass().removeAll(
                "budget-allocation-bar-ok", "budget-allocation-bar-warn", "budget-allocation-bar-over");
        if (pctDouble > 1.0) {
            allocationProgress.getStyleClass().add("budget-allocation-bar-over");
        } else if (pctDouble >= 0.9) {
            allocationProgress.getStyleClass().add("budget-allocation-bar-warn");
        } else {
            allocationProgress.getStyleClass().add("budget-allocation-bar-ok");
        }

        allocationLabel.setText("Распределено " + totalPct.setScale(0, RoundingMode.HALF_UP) + "%");

        boolean over = pctDouble > 1.0;
        allocationWarning.setVisible(over);
        allocationWarning.setManaged(over);
        if (over) {
            allocationWarning.setText("⚠ Превышение 100%!");
        }
    }

    // ── Groups section ───────────────────────────────────────────────

    private void updateGroups(MonthlyPlan plan) {
        groupsBox.getChildren().clear();

        if (plan.groups().isEmpty()) {
            Label empty = new Label("Нет групп. Создайте первую группу бюджета.");
            empty.getStyleClass().add("dash-empty");
            empty.setMaxWidth(Double.MAX_VALUE);
            empty.setAlignment(Pos.CENTER);
            groupsBox.getChildren().add(empty);
            return;
        }

        for (GroupBudget gb : plan.groups()) {
            groupsBox.getChildren().add(buildGroupCard(gb));
        }
    }

    private VBox buildGroupCard(GroupBudget gb) {
        VBox card = new VBox(10);
        card.getStyleClass().add("budget-group-card");

        // ── Заголовок: цвет + название + % + сумма ──────────────────
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Rectangle colorDot = new Rectangle(12, 12);
        colorDot.getStyleClass().add("budget-indicator");
        colorDot.setArcWidth(4);
        colorDot.setArcHeight(4);
        try {
            colorDot.setFill(Color.web(gb.group().getColor() != null ? gb.group().getColor() : "#888"));
        } catch (Exception e) {
            colorDot.setFill(Color.GRAY);
        }

        Label nameLabel = new Label(gb.group().getName());
        nameLabel.getStyleClass().add("budget-group-name");
        HBox.setHgrow(nameLabel, Priority.ALWAYS);
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        Label pctLabel = new Label(gb.group().getPercentage().setScale(0, RoundingMode.HALF_UP) + "%");
        pctLabel.getStyleClass().add("budget-group-pct");

        Label plannedLabel = new Label(CurrencyFormatter.format(gb.planned()));
        plannedLabel.getStyleClass().add("budget-group-planned");

        // Кнопки управления
        Button editBtn = new Button("✎");
        editBtn.getStyleClass().add("btn-icon");
        editBtn.setOnAction(e -> showEditGroupDialog(gb.group()));

        Button deleteBtn = new Button("✕");
        deleteBtn.getStyleClass().addAll("btn-icon", "btn-icon-danger");
        deleteBtn.setOnAction(e -> confirmDeleteGroup(gb.group()));

        header.getChildren().addAll(colorDot, nameLabel, pctLabel, plannedLabel, editBtn, deleteBtn);

        // ── ProgressBar исполнения ───────────────────────────────────
        ProgressBar bar = new ProgressBar(Math.min(gb.ratio(), 1.0));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("budget-progress-bar");
        if (gb.ratio() > 1.0) {
            bar.getStyleClass().add("budget-bar-over");
        } else if (gb.ratio() >= 0.8) {
            bar.getStyleClass().add("budget-bar-warn");
        } else {
            bar.getStyleClass().add("budget-bar-ok");
        }

        // ── Суммы ────────────────────────────────────────────────────
        HBox amounts = new HBox(8);
        amounts.setAlignment(Pos.CENTER_LEFT);

        Label spentLabel = new Label("Потрачено " + CurrencyFormatter.format(gb.spent()));
        spentLabel.getStyleClass().add("budget-spent-label");

        Label outOf = new Label("из " + CurrencyFormatter.format(gb.planned()));
        outOf.getStyleClass().add("budget-limit-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String remainingText = gb.remaining().compareTo(BigDecimal.ZERO) >= 0
                ? "осталось " + CurrencyFormatter.format(gb.remaining())
                : "перерасход " + CurrencyFormatter.format(gb.remaining().negate());
        Label remainLabel = new Label(remainingText);
        remainLabel.getStyleClass().add(
                gb.remaining().compareTo(BigDecimal.ZERO) >= 0 ? "budget-remain-ok" : "budget-remain-over");

        amounts.getChildren().addAll(spentLabel, outOf, spacer, remainLabel);

        card.getChildren().addAll(header, bar, amounts);
        return card;
    }

    // ── Categories section ───────────────────────────────────────────

    private void updateCategories() {
        categoriesBox.getChildren().clear();

        CategoryRepository catRepo = new CategoryRepository();
        List<Category> expenses = catRepo.findByType(CategoryType.EXPENSE);
        List<BudgetGroup> groups = service.getGroups();

        if (expenses.isEmpty()) {
            Label empty = new Label("Нет категорий расходов");
            empty.getStyleClass().add("dash-empty");
            empty.setMaxWidth(Double.MAX_VALUE);
            categoriesBox.getChildren().add(empty);
            return;
        }

        for (Category cat : expenses) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("budget-cat-row");

            Label nameLabel = new Label(cat.getName());
            nameLabel.getStyleClass().add("budget-cat-name");
            HBox.setHgrow(nameLabel, Priority.ALWAYS);
            nameLabel.setMaxWidth(Double.MAX_VALUE);

            // ComboBox группы
            ComboBox<BudgetGroup> groupCombo = new ComboBox<>();
            groupCombo.getStyleClass().add("category-group-selector");
            groupCombo.setPrefWidth(150);

            // "Без группы" = null через специальный объект-пустышку
            List<BudgetGroup> comboItems = new ArrayList<>();
            comboItems.add(null); // null = без группы
            comboItems.addAll(groups);
            groupCombo.setItems(FXCollections.observableArrayList(comboItems));
            groupCombo.setValue(cat.getGroup());

            groupCombo.setConverter(new javafx.util.StringConverter<>() {
                @Override
                public String toString(BudgetGroup g) {
                    return g == null ? "— Без группы —" : g.getName();
                }
                @Override
                public BudgetGroup fromString(String s) { return null; }
            });

            groupCombo.setOnAction(e -> {
                BudgetGroup selected = groupCombo.getValue();
                service.assignCategory(cat, selected);
                refresh();
            });

            row.getChildren().addAll(nameLabel, groupCombo);
            categoriesBox.getChildren().add(row);
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────

    @FXML
    private void showAddGroupDialog() {
        Optional<BudgetGroup> result = buildGroupDialog(null).showAndWait();
        result.ifPresent(g -> {
            try {
                service.createGroup(g.getName(), g.getPercentage(), g.getColor());
                refresh();
            } catch (IllegalArgumentException ex) {
                showError("Ошибка", ex.getMessage());
            }
        });
    }

    private void showEditGroupDialog(BudgetGroup group) {
        Optional<BudgetGroup> result = buildGroupDialog(group).showAndWait();
        result.ifPresent(updated -> {
            group.setName(updated.getName());
            group.setPercentage(updated.getPercentage());
            group.setColor(updated.getColor());
            try {
                service.updateGroup(group);
                refresh();
            } catch (IllegalArgumentException ex) {
                showError("Ошибка", ex.getMessage());
            }
        });
    }

    private void confirmDeleteGroup(BudgetGroup group) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Удалить группу");
        confirm.setHeaderText("Удалить группу «" + group.getName() + "»?");
        confirm.setContentText("Все категории этой группы будут отвязаны.");
        styleAlert(confirm);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                service.deleteGroup(group);
                refresh();
            }
        });
    }

    /**
     * Строит диалог создания/редактирования группы.
     * @param existing null — создание, иначе — редактирование
     */
    private Dialog<BudgetGroup> buildGroupDialog(BudgetGroup existing) {
        Dialog<BudgetGroup> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Новая группа" : "Редактировать группу");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getStyleClass().add("styled-dialog");
        dialog.getDialogPane().getScene().getStylesheets().add(
                getClass().getResource("/org/example/css/styles.css").toExternalForm());

        ButtonType okType = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(okType);
        okBtn.getStyleClass().add("default-button");

        // Поля формы
        VBox content = new VBox(14);
        content.getStyleClass().add("dialog-content");
        content.setPrefWidth(360);

        TextField nameField = new TextField(existing != null ? existing.getName() : "");
        nameField.setPromptText("Название группы");
        nameField.getStyleClass().add("text-field");

        TextField pctField = new TextField(
                existing != null ? existing.getPercentage().toPlainString() : "");
        pctField.setPromptText("Процент (0–100)");
        pctField.getStyleClass().add("text-field");

        // Выбор цвета из preset
        Label colorLabel = new Label("Цвет:");
        colorLabel.getStyleClass().add("field-label");

        HBox colorRow = new HBox(8);
        colorRow.setAlignment(Pos.CENTER_LEFT);
        final String[] selectedColor = {existing != null && existing.getColor() != null
                ? existing.getColor() : PRESET_COLORS.get(0)};

        List<Rectangle> colorRects = new ArrayList<>();
        for (String hex : PRESET_COLORS) {
            Rectangle rect = new Rectangle(22, 22);
            rect.setArcWidth(6);
            rect.setArcHeight(6);
            try { rect.setFill(Color.web(hex)); } catch (Exception ignored) {}
            rect.setStrokeWidth(2);
            rect.setStroke(hex.equals(selectedColor[0]) ? Color.WHITE : Color.TRANSPARENT);
            rect.setCursor(javafx.scene.Cursor.HAND);
            rect.setOnMouseClicked(ev -> {
                selectedColor[0] = hex;
                colorRects.forEach(r -> r.setStroke(Color.TRANSPARENT));
                rect.setStroke(Color.WHITE);
            });
            colorRects.add(rect);
            colorRow.getChildren().add(rect);
        }

        content.getChildren().addAll(
                buildFieldGroup("Название", nameField),
                buildFieldGroup("Процент (%)", pctField),
                colorLabel,
                colorRow
        );

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn != okType) return null;
            String nameVal = nameField.getText().trim();
            String pctText = pctField.getText().trim().replace(",", ".");
            if (nameVal.isEmpty()) {
                showError("Ошибка", "Введите название группы");
                return null;
            }
            BigDecimal pct;
            try {
                pct = new BigDecimal(pctText);
            } catch (NumberFormatException ex) {
                showError("Ошибка", "Некорректный процент");
                return null;
            }
            return BudgetGroup.builder()
                    .name(nameVal)
                    .percentage(pct)
                    .color(selectedColor[0])
                    .build();
        });

        return dialog;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private VBox buildFieldGroup(String labelText, javafx.scene.Node field) {
        VBox box = new VBox(5);
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("field-label");
        box.getChildren().addAll(lbl, field);
        return box;
    }

    private void showError(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        styleAlert(alert);
        alert.showAndWait();
    }

    private void styleAlert(Alert alert) {
        alert.getDialogPane().getStyleClass().add("styled-dialog");
        alert.getDialogPane().getScene().getStylesheets().add(
                getClass().getResource("/org/example/css/styles.css").toExternalForm());
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
