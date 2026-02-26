package org.example.controller;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import org.example.model.entity.SavingsGoal;
import org.example.model.enums.GoalStatus;
import org.example.repository.GoalRepository;
import org.example.repository.GoalRepository.GoalSort;
import org.example.service.GoalService;
import org.example.util.CurrencyFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class GoalController {

    private static final Logger log = LoggerFactory.getLogger(GoalController.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final int CARD_WIDTH = 270;

    @FXML private FlowPane  goalsPane;
    @FXML private ComboBox<GoalSort> sortCombo;

    private GoalService service;

    @FXML
    public void initialize() {
        service = new GoalService(new GoalRepository());

        sortCombo.getItems().addAll(GoalSort.values());
        sortCombo.setConverter(new StringConverter<>() {
            @Override public String toString(GoalSort s) {
                return switch (s) {
                    case BY_PROGRESS -> "По прогрессу";
                    case BY_DEADLINE -> "По дедлайну";
                };
            }
            @Override public GoalSort fromString(String s) { return null; }
        });
        sortCombo.setValue(GoalSort.BY_DEADLINE);
        sortCombo.valueProperty().addListener((obs, old, val) -> refresh());

        refresh();
    }

    private void refresh() {
        GoalSort sort = sortCombo.getValue() != null ? sortCombo.getValue() : GoalSort.BY_DEADLINE;
        List<SavingsGoal> goals = service.getAll(sort);
        goalsPane.getChildren().clear();
        if (goals.isEmpty()) {
            goalsPane.getChildren().add(buildEmptyState());
        } else {
            goals.forEach(g -> goalsPane.getChildren().add(buildCard(g)));
        }
    }

    private VBox buildCard(SavingsGoal goal) {
        VBox card = new VBox(14);
        card.getStyleClass().add("goal-card");
        card.setPrefWidth(CARD_WIDTH);
        card.setMaxWidth(CARD_WIDTH);

        // Статус
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label badge = buildStatusBadge(goal.getStatus());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        topRow.getChildren().addAll(badge, spacer);

        // Название
        Label nameLabel = new Label(goal.getName());
        nameLabel.getStyleClass().add("goal-card-title");
        nameLabel.setWrapText(true);

        // Описание (если есть)
        if (goal.getDescription() != null && !goal.getDescription().isBlank()) {
            Label desc = new Label(goal.getDescription());
            desc.getStyleClass().add("goal-card-desc");
            desc.setWrapText(true);
            card.getChildren().addAll(topRow, nameLabel, desc);
        } else {
            card.getChildren().addAll(topRow, nameLabel);
        }

        // Прогресс-бар
        double ratio = computeRatio(goal);
        ProgressBar bar = new ProgressBar(Math.min(ratio, 1.0));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("goal-progress-bar");
        if (goal.getStatus() == GoalStatus.COMPLETED) {
            bar.getStyleClass().add("goal-progress-complete");
        } else if (ratio >= 0.75) {
            bar.getStyleClass().add("goal-progress-high");
        }

        // Суммы
        HBox amounts = new HBox();
        amounts.setAlignment(Pos.CENTER_LEFT);
        Label current = new Label(CurrencyFormatter.format(goal.getCurrentAmount()));
        current.getStyleClass().add("goal-amount-current");
        Region amtSpacer = new Region();
        HBox.setHgrow(amtSpacer, Priority.ALWAYS);
        Label target = new Label(CurrencyFormatter.format(goal.getTargetAmount()));
        target.getStyleClass().add("goal-amount-target");
        amounts.getChildren().addAll(current, amtSpacer, target);

        // Процент
        int percent = (int) Math.min(ratio * 100, 100);
        Label percentLabel = new Label(percent + "%");
        percentLabel.getStyleClass().add("goal-percent");

        card.getChildren().addAll(buildSeparator(), bar, amounts, percentLabel);

        // Дедлайн
        if (goal.getDeadline() != null) {
            Label dl = new Label("Дедлайн: " + goal.getDeadline().format(DATE_FMT));
            dl.getStyleClass().add("goal-deadline");
            boolean overdue = goal.getStatus() == GoalStatus.ACTIVE
                    && goal.getDeadline().isBefore(LocalDate.now());
            if (overdue) dl.getStyleClass().add("goal-deadline-overdue");
            card.getChildren().add(dl);
        }

        // ── Кнопки действий ──
        if (goal.getStatus() == GoalStatus.ACTIVE) {
            HBox actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_LEFT);

            Button contributeBtn = new Button("Пополнить");
            contributeBtn.getStyleClass().add("btn-primary");
            contributeBtn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(contributeBtn, Priority.ALWAYS);
            contributeBtn.setOnAction(e -> openContributeDialog(goal));

            Button cancelBtn = new Button("Отменить");
            cancelBtn.getStyleClass().add("btn-secondary");
            cancelBtn.setOnAction(e -> confirmCancel(goal));

            actions.getChildren().addAll(contributeBtn, cancelBtn);
            card.getChildren().add(actions);
        }

        return card;
    }

    private Label buildStatusBadge(GoalStatus status) {
        Label badge = new Label();
        badge.getStyleClass().add("goal-status-badge");
        switch (status) {
            case ACTIVE    -> { badge.setText("Активна");    badge.getStyleClass().add("goal-badge-active"); }
            case COMPLETED -> { badge.setText("Достигнута"); badge.getStyleClass().add("goal-badge-complete"); }
            case CANCELLED -> { badge.setText("Отменена");  badge.getStyleClass().add("goal-badge-cancelled"); }
        }
        return badge;
    }

    private Separator buildSeparator() {
        Separator sep = new Separator();
        sep.getStyleClass().add("goal-separator");
        return sep;
    }

    private double computeRatio(SavingsGoal goal) {
        if (goal.getTargetAmount().compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return goal.getCurrentAmount()
                   .divide(goal.getTargetAmount(), 4, RoundingMode.HALF_UP)
                   .doubleValue();
    }


    private Node buildEmptyState() {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.setPrefWidth(500);
        box.setPadding(new Insets(60, 0, 0, 0));

        Label icon = new Label("◎");
        icon.getStyleClass().add("goal-empty-icon");

        Label title = new Label("Нет целей накопления");
        title.getStyleClass().add("goal-empty-title");

        Label hint = new Label("Нажмите «+ Новая цель», чтобы начать откладывать на важное.");
        hint.getStyleClass().add("goal-empty-hint");
        hint.setWrapText(true);
        hint.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        box.getChildren().addAll(icon, title, hint);
        return box;
    }

    // окно создания цели

    @FXML
    private void openAddDialog() {
        Dialog<SavingsGoal> dialog = new Dialog<>();
        dialog.setTitle("Новая цель накопления");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().addAll(goalsPane.getScene().getStylesheets());

        GridPane grid = buildDialogGrid();

        TextField nameField = new TextField();
        nameField.setPromptText("Название цели");
        nameField.setPrefWidth(240);

        TextField targetField = new TextField();
        targetField.setPromptText("0.00");
        targetField.setPrefWidth(240);

        DatePicker deadlinePicker = new DatePicker();
        deadlinePicker.setPromptText("Необязательно");
        deadlinePicker.setPrefWidth(240);

        TextField descField = new TextField();
        descField.setPromptText("Необязательно");
        descField.setPrefWidth(240);

        addRow(grid, 0, "Название:",    nameField);
        addRow(grid, 1, "Сумма, ₽:",   targetField);
        addRow(grid, 2, "Дедлайн:",    deadlinePicker);
        addRow(grid, 3, "Описание:",   descField);

        dialog.getDialogPane().setContent(grid);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);
        Runnable validate = () -> {
            boolean valid;
            try {
                BigDecimal a = new BigDecimal(targetField.getText().replace(",", ".").trim());
                valid = !nameField.getText().isBlank() && a.compareTo(BigDecimal.ZERO) > 0;
            } catch (NumberFormatException e) {
                valid = false;
            }
            okBtn.setDisable(!valid);
        };
        nameField.textProperty().addListener((obs, o, n)  -> validate.run());
        targetField.textProperty().addListener((obs, o, n) -> validate.run());
        validate.run();

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            try {
                BigDecimal target = new BigDecimal(targetField.getText().replace(",", ".").trim());
                return service.create(nameField.getText(), target,
                        deadlinePicker.getValue(), descField.getText());
            } catch (NumberFormatException e) {
                log.warn("Некорректная сумма цели: {}", targetField.getText());
                return null;
            }
        });

        dialog.showAndWait().ifPresent(g -> refresh());
    }

    // Пополнение цели

    private void openContributeDialog(SavingsGoal goal) {
        Dialog<BigDecimal> dialog = new Dialog<>();
        dialog.setTitle("Пополнить цель");
        dialog.setHeaderText(goal.getName());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().addAll(goalsPane.getScene().getStylesheets());

        BigDecimal remaining = goal.getTargetAmount().subtract(goal.getCurrentAmount());

        GridPane grid = buildDialogGrid();

        Label infoLabel = new Label(String.format("Осталось: %s", CurrencyFormatter.format(remaining)));
        infoLabel.getStyleClass().add("goal-dialog-info");

        TextField amountField = new TextField();
        amountField.setPromptText("0.00");
        amountField.setPrefWidth(240);

        grid.add(infoLabel, 0, 0, 2, 1);
        addRow(grid, 1, "Сумма, ₽:", amountField);

        dialog.getDialogPane().setContent(grid);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);
        amountField.textProperty().addListener((obs, o, n) -> {
            boolean valid;
            try {
                BigDecimal a = new BigDecimal(n.replace(",", ".").trim());
                valid = a.compareTo(BigDecimal.ZERO) > 0;
            } catch (NumberFormatException e) {
                valid = false;
            }
            okBtn.setDisable(!valid);
        });
        okBtn.setDisable(true);

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            try {
                return new BigDecimal(amountField.getText().replace(",", ".").trim());
            } catch (NumberFormatException e) {
                return null;
            }
        });

        dialog.showAndWait().ifPresent(amount -> {
            service.contribute(goal, amount);
            refresh();
        });
    }

    // Отмена целb

    private void confirmCancel(SavingsGoal goal) {
        ButtonType cancelGoalType = new ButtonType("Отменить цель", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeType = new ButtonType("Закрыть", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert confirm = new Alert(Alert.AlertType.NONE);
        confirm.setTitle("Отменить цель");
        confirm.setHeaderText("Отменить цель «" + goal.getName() + "»?");
        confirm.setContentText("Накопленная сумма " + CurrencyFormatter.format(goal.getCurrentAmount()) +
                " сохранится, но цель будет помечена как отменённая.");
        confirm.getButtonTypes().setAll(cancelGoalType, closeType);

        Label icon = new Label("✕");
        icon.setStyle(
            "-fx-text-fill: #EF4444; -fx-font-size: 16px; -fx-font-weight: bold;" +
            "-fx-background-color: rgba(239,68,68,0.12);" +
            "-fx-background-radius: 8; -fx-padding: 7 11;"
        );
        confirm.setGraphic(icon);

        confirm.getDialogPane().getStylesheets().addAll(goalsPane.getScene().getStylesheets());
        confirm.getDialogPane().getStyleClass().add("danger-dialog");

        confirm.showAndWait()
               .filter(b -> b == cancelGoalType)
               .ifPresent(b -> { service.cancel(goal); refresh(); });
    }

    // ------------------------------------------------------------------ helpers

    private GridPane buildDialogGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        grid.setPadding(new Insets(20, 24, 8, 24));
        return grid;
    }

    private void addRow(GridPane grid, int row, String labelText, Node control) {
        Label label = new Label(labelText);
        grid.add(label, 0, row);
        grid.add(control, 1, row);
    }
}
