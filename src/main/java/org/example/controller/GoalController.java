package org.example.controller;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import org.example.model.entity.Debt;
import org.example.model.entity.SavingsGoal;
import org.example.model.enums.DebtStatus;
import org.example.model.enums.GoalStatus;
import org.example.repository.DebtRepository;
import org.example.repository.DebtRepository.DebtSort;
import org.example.repository.GoalRepository;
import org.example.repository.GoalRepository.GoalSort;
import org.example.service.DebtService;
import org.example.service.GoalService;
import org.example.util.CurrencyFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class GoalController {

    private static final Logger log = LoggerFactory.getLogger(GoalController.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final int CARD_WIDTH = 270;

    // ── Savings tab ──
    @FXML private FlowPane  goalsPane;
    @FXML private ComboBox<GoalSort> sortCombo;

    // ── Debt tab ──
    @FXML private FlowPane  debtsPane;
    @FXML private ComboBox<DebtSort> debtSortCombo;

    // ── Shared ──
    @FXML private TabPane   tabPane;
    @FXML private Button    addBtn;

    private GoalService goalService;
    private DebtService debtService;

    @FXML
    public void initialize() {
        goalService = new GoalService(new GoalRepository());
        debtService = new DebtService(new DebtRepository());

        // ── Savings sort ──
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
        sortCombo.valueProperty().addListener((obs, old, val) -> refreshGoals());

        // ── Debt sort ──
        debtSortCombo.getItems().addAll(DebtSort.values());
        debtSortCombo.setConverter(new StringConverter<>() {
            @Override public String toString(DebtSort s) {
                if (s == null) return "";
                return switch (s) {
                    case BY_REMAINING    -> "По остатку";
                    case BY_NEXT_PAYMENT -> "По дате платежа";
                };
            }
            @Override public DebtSort fromString(String s) { return null; }
        });
        debtSortCombo.setValue(DebtSort.BY_NEXT_PAYMENT);
        debtSortCombo.valueProperty().addListener((obs, old, val) -> refreshDebts());

        // ── Tab change: update addBtn text ──
        tabPane.getSelectionModel().selectedIndexProperty().addListener((obs, old, idx) -> {
            boolean isDebtTab = idx.intValue() == 1;
            addBtn.setText(isDebtTab ? "Новый долг" : "Новая цель");
        });

        refreshGoals();
        refreshDebts();
    }

    // ── Dispatcher: called by the header "Add" button ──────────────

    @FXML
    private void onAddClick() {
        if (tabPane.getSelectionModel().getSelectedIndex() == 1) {
            openAddDebtDialog();
        } else {
            openAddGoalDialog();
        }
    }

    // ================================================================
    // SAVINGS GOALS TAB
    // ================================================================

    private void refreshGoals() {
        GoalSort sort = sortCombo.getValue() != null ? sortCombo.getValue() : GoalSort.BY_DEADLINE;
        List<SavingsGoal> goals = goalService.getAll(sort);
        goalsPane.getChildren().clear();
        if (goals.isEmpty()) {
            goalsPane.getChildren().add(buildGoalEmptyState());
        } else {
            goals.forEach(g -> goalsPane.getChildren().add(buildGoalCard(g)));
        }
    }

    private VBox buildGoalCard(SavingsGoal goal) {
        VBox card = new VBox(14);
        card.getStyleClass().add("goal-card");
        card.setPrefWidth(CARD_WIDTH);
        card.setMaxWidth(CARD_WIDTH);

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label badge = buildGoalStatusBadge(goal.getStatus());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        topRow.getChildren().addAll(badge, spacer);

        Label nameLabel = new Label(goal.getName());
        nameLabel.getStyleClass().add("goal-card-title");
        nameLabel.setWrapText(true);

        if (goal.getDescription() != null && !goal.getDescription().isBlank()) {
            Label desc = new Label(goal.getDescription());
            desc.getStyleClass().add("goal-card-desc");
            desc.setWrapText(true);
            card.getChildren().addAll(topRow, nameLabel, desc);
        } else {
            card.getChildren().addAll(topRow, nameLabel);
        }

        double ratio = computeGoalRatio(goal);
        ProgressBar bar = new ProgressBar(Math.min(ratio, 1.0));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("goal-progress-bar");
        if (goal.getStatus() == GoalStatus.COMPLETED) {
            bar.getStyleClass().add("goal-progress-complete");
        } else if (ratio >= 0.75) {
            bar.getStyleClass().add("goal-progress-high");
        }

        HBox amounts = new HBox();
        amounts.setAlignment(Pos.CENTER_LEFT);
        Label current = new Label(CurrencyFormatter.format(goal.getCurrentAmount()));
        current.getStyleClass().add("goal-amount-current");
        Region amtSpacer = new Region();
        HBox.setHgrow(amtSpacer, Priority.ALWAYS);
        Label target = new Label(CurrencyFormatter.format(goal.getTargetAmount()));
        target.getStyleClass().add("goal-amount-target");
        amounts.getChildren().addAll(current, amtSpacer, target);

        int percent = (int) Math.min(ratio * 100, 100);
        Label percentLabel = new Label(percent + "%");
        percentLabel.getStyleClass().add("goal-percent");

        card.getChildren().addAll(buildSeparator(), bar, amounts, percentLabel);

        if (goal.getDeadline() != null) {
            Label dl = new Label("Дедлайн: " + goal.getDeadline().format(DATE_FMT));
            dl.getStyleClass().add("goal-deadline");
            boolean overdue = goal.getStatus() == GoalStatus.ACTIVE
                    && goal.getDeadline().isBefore(LocalDate.now());
            if (overdue) dl.getStyleClass().add("goal-deadline-overdue");
            card.getChildren().add(dl);
        }

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
            cancelBtn.setOnAction(e -> confirmCancelGoal(goal));

            actions.getChildren().addAll(contributeBtn, cancelBtn);
            card.getChildren().add(actions);
        } else {
            Button deleteBtn = new Button("Удалить");
            deleteBtn.getStyleClass().addAll("btn-danger");
            deleteBtn.setMaxWidth(Double.MAX_VALUE);
            deleteBtn.setOnAction(e -> confirmDeleteGoal(goal));
            card.getChildren().add(deleteBtn);
        }

        return card;
    }

    private Label buildGoalStatusBadge(GoalStatus status) {
        Label badge = new Label();
        badge.getStyleClass().add("goal-status-badge");
        switch (status) {
            case ACTIVE    -> { badge.setText("Активна");    badge.getStyleClass().add("goal-badge-active"); }
            case COMPLETED -> { badge.setText("Достигнута"); badge.getStyleClass().add("goal-badge-complete"); }
            case CANCELLED -> { badge.setText("Отменена");   badge.getStyleClass().add("goal-badge-cancelled"); }
        }
        return badge;
    }

    private double computeGoalRatio(SavingsGoal goal) {
        if (goal.getTargetAmount().compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return goal.getCurrentAmount()
                   .divide(goal.getTargetAmount(), 4, RoundingMode.HALF_UP)
                   .doubleValue();
    }

    private Node buildGoalEmptyState() {
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

    @FXML
    private void openAddGoalDialog() {
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

        addRow(grid, 0, "Название:",   nameField);
        addRow(grid, 1, "Сумма, ₽:",  targetField);
        addRow(grid, 2, "Дедлайн:",   deadlinePicker);
        addRow(grid, 3, "Описание:",  descField);

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
        nameField.textProperty().addListener((obs, o, n) -> validate.run());
        targetField.textProperty().addListener((obs, o, n) -> validate.run());
        validate.run();

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            try {
                BigDecimal target = new BigDecimal(targetField.getText().replace(",", ".").trim());
                return goalService.create(nameField.getText(), target,
                        deadlinePicker.getValue(), descField.getText());
            } catch (NumberFormatException e) {
                log.warn("Некорректная сумма цели: {}", targetField.getText());
                return null;
            }
        });

        dialog.showAndWait().ifPresent(g -> refreshGoals());
    }

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
            goalService.contribute(goal, amount);
            refreshGoals();
        });
    }

    private void confirmCancelGoal(SavingsGoal goal) {
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
               .ifPresent(b -> { goalService.cancel(goal); refreshGoals(); });
    }

    private void confirmDeleteGoal(SavingsGoal goal) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Удалить цель");
        confirm.setHeaderText("Удалить цель «" + goal.getName() + "»?");
        confirm.setContentText("Это действие необратимо. Цель будет удалена навсегда.");
        confirm.getDialogPane().getStylesheets().addAll(goalsPane.getScene().getStylesheets());

        confirm.showAndWait()
               .filter(b -> b == ButtonType.OK)
               .ifPresent(b -> { goalService.delete(goal); refreshGoals(); });
    }

    // ================================================================
    // DEBTS TAB
    // ================================================================

    private void refreshDebts() {
        DebtSort sort = debtSortCombo.getValue() != null
                ? debtSortCombo.getValue() : DebtSort.BY_NEXT_PAYMENT;
        List<Debt> debts = debtService.getAll(sort);
        debtsPane.getChildren().clear();
        if (debts.isEmpty()) {
            debtsPane.getChildren().add(buildDebtEmptyState());
        } else {
            debts.forEach(d -> debtsPane.getChildren().add(buildDebtCard(d)));
        }
    }

    private VBox buildDebtCard(Debt debt) {
        VBox card = new VBox(14);
        card.getStyleClass().add("goal-card");
        card.setPrefWidth(CARD_WIDTH);
        card.setMaxWidth(CARD_WIDTH);

        // ── Top row: status badge + creditor ──
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label badge = buildDebtStatusBadge(debt.getStatus());
        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        topRow.getChildren().addAll(badge, topSpacer);
        if (debt.getCreditorName() != null) {
            Label creditor = new Label(debt.getCreditorName());
            creditor.getStyleClass().add("debt-creditor");
            topRow.getChildren().add(creditor);
        }

        // ── Name ──
        Label nameLabel = new Label(debt.getName());
        nameLabel.getStyleClass().add("goal-card-title");
        nameLabel.setWrapText(true);

        card.getChildren().addAll(topRow, nameLabel);

        // ── Progress bar ──
        BigDecimal remaining = debt.getTotalAmount().subtract(debt.getPaidAmount())
                                   .max(BigDecimal.ZERO);
        double ratio = computeDebtRatio(debt);
        ProgressBar bar = new ProgressBar(Math.min(ratio, 1.0));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("goal-progress-bar");
        if (debt.getStatus() == DebtStatus.PAID) {
            bar.getStyleClass().add("goal-progress-complete");
        } else if (ratio >= 0.75) {
            bar.getStyleClass().add("goal-progress-high");
        }

        // ── Amounts ──
        HBox amounts = new HBox();
        amounts.setAlignment(Pos.CENTER_LEFT);
        Label paidLbl = new Label("Выплачено: " + CurrencyFormatter.format(debt.getPaidAmount()));
        paidLbl.getStyleClass().add("goal-amount-current");
        Region amtSpacer = new Region();
        HBox.setHgrow(amtSpacer, Priority.ALWAYS);
        Label remLbl = new Label("Остаток: " + CurrencyFormatter.format(remaining));
        remLbl.getStyleClass().add("goal-amount-target");
        amounts.getChildren().addAll(paidLbl, amtSpacer, remLbl);

        int percent = (int) Math.min(ratio * 100, 100);
        Label percentLabel = new Label(percent + "%");
        percentLabel.getStyleClass().add("goal-percent");

        card.getChildren().addAll(buildSeparator(), bar, amounts, percentLabel);

        // ── Interest rate + overpayment ──
        if (debt.getInterestRate() != null) {
            HBox rateRow = new HBox(8);
            rateRow.setAlignment(Pos.CENTER_LEFT);
            Label rateLbl = new Label("Ставка: " +
                    debt.getInterestRate().stripTrailingZeros().toPlainString() + "% год.");
            rateLbl.getStyleClass().add("debt-rate-info");
            rateRow.getChildren().add(rateLbl);

            BigDecimal overpayment = computeOverpayment(debt, remaining);
            if (overpayment != null && overpayment.compareTo(BigDecimal.ZERO) > 0) {
                Region rs = new Region();
                HBox.setHgrow(rs, Priority.ALWAYS);
                Label ovLbl = new Label("~" + CurrencyFormatter.format(overpayment) + " переплата");
                ovLbl.getStyleClass().add("debt-overpayment");
                rateRow.getChildren().addAll(rs, ovLbl);
            }
            card.getChildren().add(rateRow);
        }

        // ── Minimum payment ──
        if (debt.getMinimumPayment() != null) {
            Label minLbl = new Label("Мин. платёж: " +
                    CurrencyFormatter.format(debt.getMinimumPayment()) + "/мес");
            minLbl.getStyleClass().add("debt-rate-info");
            card.getChildren().add(minLbl);
        }

        // ── Next payment date ──
        if (debt.getNextPaymentDate() != null) {
            Label npLbl = new Label("Следующий платёж: " +
                    debt.getNextPaymentDate().format(DATE_FMT));
            npLbl.getStyleClass().add("debt-next-payment");
            boolean overdue = debt.getStatus() == DebtStatus.ACTIVE
                    && debt.getNextPaymentDate().isBefore(LocalDate.now());
            if (overdue) npLbl.getStyleClass().add("debt-next-payment-overdue");
            card.getChildren().add(npLbl);
        }

        // ── Deadline ──
        if (debt.getDeadline() != null) {
            Label dl = new Label("Погасить до: " + debt.getDeadline().format(DATE_FMT));
            dl.getStyleClass().add("goal-deadline");
            boolean overdue = debt.getStatus() == DebtStatus.ACTIVE
                    && debt.getDeadline().isBefore(LocalDate.now());
            if (overdue) dl.getStyleClass().add("goal-deadline-overdue");
            card.getChildren().add(dl);
        }

        // ── Actions ──
        if (debt.getStatus() == DebtStatus.ACTIVE) {
            HBox actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_LEFT);

            Button repayBtn = new Button("Внести платёж");
            repayBtn.getStyleClass().add("btn-primary");
            repayBtn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(repayBtn, Priority.ALWAYS);
            repayBtn.setOnAction(e -> openRepayDialog(debt));

            Button cancelBtn = new Button("Отменить");
            cancelBtn.getStyleClass().add("btn-secondary");
            cancelBtn.setOnAction(e -> confirmCancelDebt(debt));

            actions.getChildren().addAll(repayBtn, cancelBtn);
            card.getChildren().add(actions);
        } else {
            Button deleteBtn = new Button("Удалить");
            deleteBtn.getStyleClass().add("btn-danger");
            deleteBtn.setMaxWidth(Double.MAX_VALUE);
            deleteBtn.setOnAction(e -> confirmDeleteDebt(debt));
            card.getChildren().add(deleteBtn);
        }

        return card;
    }

    private Label buildDebtStatusBadge(DebtStatus status) {
        Label badge = new Label();
        badge.getStyleClass().add("goal-status-badge");
        switch (status) {
            case ACTIVE    -> { badge.setText("Активен");  badge.getStyleClass().add("goal-badge-active"); }
            case PAID      -> { badge.setText("Погашен");  badge.getStyleClass().add("goal-badge-complete"); }
            case CANCELLED -> { badge.setText("Отменён");  badge.getStyleClass().add("goal-badge-cancelled"); }
        }
        return badge;
    }

    private double computeDebtRatio(Debt debt) {
        if (debt.getTotalAmount().compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return debt.getPaidAmount()
                   .divide(debt.getTotalAmount(), 4, RoundingMode.HALF_UP)
                   .doubleValue();
    }

    /**
     * Упрощённый расчёт переплаты:
     * remaining × (rate/100) × (monthsLeft/12)
     * Возвращает null если дата погашения не задана.
     */
    private BigDecimal computeOverpayment(Debt debt, BigDecimal remaining) {
        if (debt.getInterestRate() == null || debt.getDeadline() == null) return null;
        long months = ChronoUnit.MONTHS.between(LocalDate.now(), debt.getDeadline());
        if (months <= 0) return BigDecimal.ZERO;
        return remaining
                .multiply(debt.getInterestRate())
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(months))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    private Node buildDebtEmptyState() {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.setPrefWidth(500);
        box.setPadding(new Insets(60, 0, 0, 0));

        Label icon = new Label("₽");
        icon.getStyleClass().add("goal-empty-icon");

        Label title = new Label("Нет долгов");
        title.getStyleClass().add("goal-empty-title");

        Label hint = new Label("Нажмите «+ Новый долг», чтобы добавить кредит или долг.");
        hint.getStyleClass().add("goal-empty-hint");
        hint.setWrapText(true);
        hint.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        box.getChildren().addAll(icon, title, hint);
        return box;
    }

    private void openAddDebtDialog() {
        Dialog<Debt> dialog = new Dialog<>();
        dialog.setTitle("Новый долг");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().addAll(debtsPane.getScene().getStylesheets());

        GridPane grid = buildDialogGrid();

        TextField nameField = new TextField();
        nameField.setPromptText("Ипотека Сбербанк");
        nameField.setPrefWidth(240);

        TextField creditorField = new TextField();
        creditorField.setPromptText("Необязательно");
        creditorField.setPrefWidth(240);

        TextField totalField = new TextField();
        totalField.setPromptText("0.00");
        totalField.setPrefWidth(240);

        TextField rateField = new TextField();
        rateField.setPromptText("14.5 (необязательно)");
        rateField.setPrefWidth(240);

        TextField minPayField = new TextField();
        minPayField.setPromptText("0.00 (необязательно)");
        minPayField.setPrefWidth(240);

        DatePicker nextPayPicker = new DatePicker();
        nextPayPicker.setPromptText("Необязательно");
        nextPayPicker.setPrefWidth(240);

        DatePicker deadlinePicker = new DatePicker();
        deadlinePicker.setPromptText("Необязательно");
        deadlinePicker.setPrefWidth(240);

        addRow(grid, 0, "Название:",          nameField);
        addRow(grid, 1, "Кредитор:",          creditorField);
        addRow(grid, 2, "Сумма долга, ₽:",    totalField);
        addRow(grid, 3, "% годовых:",         rateField);
        addRow(grid, 4, "Мин. платёж, ₽/мес:", minPayField);
        addRow(grid, 5, "Следующий платёж:",  nextPayPicker);
        addRow(grid, 6, "Погасить до:",       deadlinePicker);

        dialog.getDialogPane().setContent(grid);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);
        Runnable validate = () -> {
            boolean valid;
            try {
                BigDecimal total = new BigDecimal(totalField.getText().replace(",", ".").trim());
                valid = !nameField.getText().isBlank() && total.compareTo(BigDecimal.ZERO) > 0;
            } catch (NumberFormatException e) {
                valid = false;
            }
            okBtn.setDisable(!valid);
        };
        nameField.textProperty().addListener((obs, o, n) -> validate.run());
        totalField.textProperty().addListener((obs, o, n) -> validate.run());
        validate.run();

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            try {
                BigDecimal total = new BigDecimal(totalField.getText().replace(",", ".").trim());
                BigDecimal rate = parseOptionalDecimal(rateField.getText());
                BigDecimal minPay = parseOptionalDecimal(minPayField.getText());
                return debtService.create(
                        nameField.getText(),
                        creditorField.getText(),
                        total, rate, minPay,
                        nextPayPicker.getValue(),
                        deadlinePicker.getValue());
            } catch (NumberFormatException e) {
                log.warn("Некорректная сумма долга: {}", totalField.getText());
                return null;
            }
        });

        dialog.showAndWait().ifPresent(d -> refreshDebts());
    }

    private void openRepayDialog(Debt debt) {
        Dialog<BigDecimal> dialog = new Dialog<>();
        dialog.setTitle("Внести платёж");
        dialog.setHeaderText(debt.getName());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().addAll(debtsPane.getScene().getStylesheets());

        BigDecimal remaining = debt.getTotalAmount().subtract(debt.getPaidAmount())
                                   .max(BigDecimal.ZERO);

        GridPane grid = buildDialogGrid();

        Label infoLabel = new Label("Остаток: " + CurrencyFormatter.format(remaining));
        infoLabel.getStyleClass().add("goal-dialog-info");

        TextField amountField = new TextField();
        if (debt.getMinimumPayment() != null) {
            amountField.setText(debt.getMinimumPayment().toPlainString());
        }
        amountField.setPromptText("0.00");
        amountField.setPrefWidth(240);

        grid.add(infoLabel, 0, 0, 2, 1);
        addRow(grid, 1, "Сумма платежа, ₽:", amountField);
        dialog.getDialogPane().setContent(grid);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);
        Runnable validate = () -> {
            boolean valid;
            try {
                BigDecimal a = new BigDecimal(amountField.getText().replace(",", ".").trim());
                valid = a.compareTo(BigDecimal.ZERO) > 0;
            } catch (NumberFormatException e) {
                valid = false;
            }
            okBtn.setDisable(!valid);
        };
        amountField.textProperty().addListener((obs, o, n) -> validate.run());
        validate.run();

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            try {
                return new BigDecimal(amountField.getText().replace(",", ".").trim());
            } catch (NumberFormatException e) {
                return null;
            }
        });

        dialog.showAndWait().ifPresent(amount -> {
            debtService.repay(debt, amount);
            refreshDebts();
        });
    }

    private void confirmCancelDebt(Debt debt) {
        ButtonType cancelType = new ButtonType("Отменить долг", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeType  = new ButtonType("Закрыть",       ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert confirm = new Alert(Alert.AlertType.NONE);
        confirm.setTitle("Отменить долг");
        confirm.setHeaderText("Отменить долг «" + debt.getName() + "»?");
        confirm.setContentText("Долг будет помечен как отменённый.");
        confirm.getButtonTypes().setAll(cancelType, closeType);

        Label icon = new Label("✕");
        icon.setStyle(
            "-fx-text-fill: #EF4444; -fx-font-size: 16px; -fx-font-weight: bold;" +
            "-fx-background-color: rgba(239,68,68,0.12);" +
            "-fx-background-radius: 8; -fx-padding: 7 11;"
        );
        confirm.setGraphic(icon);
        confirm.getDialogPane().getStylesheets().addAll(debtsPane.getScene().getStylesheets());
        confirm.getDialogPane().getStyleClass().add("danger-dialog");

        confirm.showAndWait()
               .filter(b -> b == cancelType)
               .ifPresent(b -> { debtService.cancel(debt); refreshDebts(); });
    }

    private void confirmDeleteDebt(Debt debt) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Удалить долг");
        confirm.setHeaderText("Удалить долг «" + debt.getName() + "»?");
        confirm.setContentText("Это действие необратимо.");
        confirm.getDialogPane().getStylesheets().addAll(debtsPane.getScene().getStylesheets());

        confirm.showAndWait()
               .filter(b -> b == ButtonType.OK)
               .ifPresent(b -> { debtService.delete(debt); refreshDebts(); });
    }

    // ================================================================
    // SHARED HELPERS
    // ================================================================

    private Separator buildSeparator() {
        Separator sep = new Separator();
        sep.getStyleClass().add("goal-separator");
        return sep;
    }

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

    /** Парсит необязательное decimal-поле. Возвращает null если строка пустая или 0. */
    private BigDecimal parseOptionalDecimal(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            BigDecimal val = new BigDecimal(text.replace(",", ".").trim());
            return val.compareTo(BigDecimal.ZERO) > 0 ? val : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
