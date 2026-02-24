package org.example.controller;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.service.AnalyticsService;
import org.example.service.AnalyticsService.*;
import org.example.util.CurrencyFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

public class AnalyticsController {

    private static final Locale RU     = new Locale("ru", "RU");
    private static final String P3     = "3 месяца";
    private static final String P6     = "6 месяцев";
    private static final String P12    = "12 месяцев";
    private static final String CUSTOM = "Произвольный";

    @FXML private ComboBox<String> periodCombo;
    @FXML private DatePicker       fromPicker;
    @FXML private DatePicker       toPicker;
    @FXML private VBox             barChartBox;
    @FXML private Label            avgDayLabel;
    @FXML private Label            avgWeekLabel;
    @FXML private Label            avgMonthLabel;
    @FXML private Label            topCatLabel;
    @FXML private VBox             categoriesBox;
    @FXML private VBox             goalSection;
    @FXML private VBox             goalBox;

    private AnalyticsService service;

    // ── Lifecycle ────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        service = new AnalyticsService();

        periodCombo.getItems().addAll(P3, P6, P12, CUSTOM);
        periodCombo.setValue(P6);

        fromPicker.setVisible(false);
        fromPicker.setManaged(false);
        toPicker.setVisible(false);
        toPicker.setManaged(false);

        periodCombo.valueProperty().addListener((obs, old, val) -> {
            boolean custom = CUSTOM.equals(val);
            fromPicker.setVisible(custom);
            fromPicker.setManaged(custom);
            toPicker.setVisible(custom);
            toPicker.setManaged(custom);
            if (!custom) refresh();
        });

        refresh();
    }

    @FXML
    public void refresh() {
        LocalDate[] range  = resolveRange();
        LocalDate   from   = range[0];
        LocalDate   to     = range[1];
        int         months = monthsCount();

        updateBarChart(months);
        updateAverages(from, to);
        updateTopCategory(from, to);
        updateCategories(from, to);
        updateGoals();
    }

    // ── Period helpers ────────────────────────────────────────────────

    private LocalDate[] resolveRange() {
        if (CUSTOM.equals(periodCombo.getValue())) {
            LocalDate from = fromPicker.getValue() != null
                    ? fromPicker.getValue()
                    : LocalDate.now().withDayOfMonth(1);
            LocalDate to = toPicker.getValue() != null
                    ? toPicker.getValue()
                    : LocalDate.now();
            return new LocalDate[]{from, to};
        }
        int m = monthsCount();
        return new LocalDate[]{
                YearMonth.now().minusMonths(m - 1).atDay(1),
                LocalDate.now()
        };
    }

    private int monthsCount() {
        String v = periodCombo.getValue();
        if (P3.equals(v))  return 3;
        if (P12.equals(v)) return 12;
        return 6;
    }

    // ── Bar Chart ─────────────────────────────────────────────────────

    private void updateBarChart(int months) {
        barChartBox.getChildren().clear();

        List<MonthBar> data = service.getMonthlyComparison(months);
        boolean hasData = data.stream().anyMatch(m ->
                m.income().compareTo(BigDecimal.ZERO) > 0 ||
                m.expense().compareTo(BigDecimal.ZERO) > 0);

        if (!hasData) {
            barChartBox.getChildren().add(buildEmpty("Нет данных за выбранный период"));
            return;
        }

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(true);
        yAxis.setTickLabelFormatter(new javafx.util.StringConverter<>() {
            @Override public String toString(Number n) {
                double v = n.doubleValue();
                if (v >= 1_000_000) return String.format("%.0fM", v / 1_000_000);
                if (v >= 1_000)     return String.format("%.0fk", v / 1_000);
                return String.format("%.0f", v);
            }
            @Override public Number fromString(String s) { return 0; }
        });

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setLegendVisible(true);
        chart.getStyleClass().add("analytics-bar-chart");
        chart.setCategoryGap(16);
        chart.setBarGap(4);
        VBox.setVgrow(chart, Priority.ALWAYS);
        chart.setMaxHeight(Double.MAX_VALUE);

        XYChart.Series<String, Number> incomeSeries  = new XYChart.Series<>();
        incomeSeries.setName("Доходы");
        XYChart.Series<String, Number> expenseSeries = new XYChart.Series<>();
        expenseSeries.setName("Расходы");

        for (MonthBar mb : data) {
            String lbl = capitalize(mb.month().getMonth()
                    .getDisplayName(TextStyle.SHORT_STANDALONE, RU));
            if (months > 6) lbl += " '" + String.format("%02d", mb.month().getYear() % 100);
            incomeSeries.getData().add(new XYChart.Data<>(lbl, mb.income()));
            expenseSeries.getData().add(new XYChart.Data<>(lbl, mb.expense()));
        }

        chart.getData().addAll(incomeSeries, expenseSeries);
        barChartBox.getChildren().add(chart);
    }

    // ── Averages ──────────────────────────────────────────────────────

    private void updateAverages(LocalDate from, LocalDate to) {
        Averages avg = service.getExpenseAverages(from, to);
        avgDayLabel.setText(CurrencyFormatter.format(avg.daily()));
        avgWeekLabel.setText(CurrencyFormatter.format(avg.weekly()));
        avgMonthLabel.setText(CurrencyFormatter.format(avg.monthly()));
    }

    // ── Top Category ──────────────────────────────────────────────────

    private void updateTopCategory(LocalDate from, LocalDate to) {
        topCatLabel.setText(service.getTopCategoryName(from, to));
    }

    // ── Categories ────────────────────────────────────────────────────

    private void updateCategories(LocalDate from, LocalDate to) {
        categoriesBox.getChildren().clear();

        List<CategoryAmount> cats = service.getTopCategories(from, to, 8);
        if (cats.isEmpty()) {
            categoriesBox.getChildren().add(buildEmpty("Нет расходов за выбранный период"));
            return;
        }

        BigDecimal total = cats.stream()
                .map(CategoryAmount::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (CategoryAmount ca : cats) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("dash-cat-row");

            Label name = new Label(ca.name());
            name.getStyleClass().add("dash-cat-name");
            HBox.setHgrow(name, Priority.ALWAYS);
            name.setMaxWidth(Double.MAX_VALUE);

            double ratio = total.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                    ca.amount().divide(total, 4, RoundingMode.HALF_UP).doubleValue();

            ProgressBar bar = new ProgressBar(ratio);
            bar.setPrefWidth(120);
            bar.getStyleClass().add("dash-cat-bar");

            Label pctLabel = new Label((int)(ratio * 100) + "%");
            pctLabel.getStyleClass().add("dash-cat-pct");
            pctLabel.setPrefWidth(36);

            Label amount = new Label(CurrencyFormatter.format(ca.amount()));
            amount.getStyleClass().add("dash-cat-amount");

            row.getChildren().addAll(name, bar, pctLabel, amount);
            categoriesBox.getChildren().add(row);
        }
    }

    // ── Goals ─────────────────────────────────────────────────────────

    private void updateGoals() {
        goalBox.getChildren().clear();

        List<GoalProgress> goals = service.getGoalProgress();
        goalSection.setVisible(!goals.isEmpty());
        goalSection.setManaged(!goals.isEmpty());

        for (GoalProgress gp : goals) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("analytics-goal-row");

            Label name = new Label(gp.name());
            name.getStyleClass().add("dash-cat-name");
            name.setPrefWidth(160);
            name.setMinWidth(160);

            ProgressBar bar = new ProgressBar(Math.min(gp.ratio(), 1.0));
            bar.getStyleClass().add("goal-progress-bar");
            if (gp.ratio() >= 0.75) bar.getStyleClass().add("goal-progress-high");
            HBox.setHgrow(bar, Priority.ALWAYS);
            bar.setMaxWidth(Double.MAX_VALUE);

            int pct = (int)(Math.min(gp.ratio(), 1.0) * 100);
            Label pctLabel = new Label(pct + "%");
            pctLabel.getStyleClass().add("goal-percent");
            pctLabel.setPrefWidth(40);

            Label amounts = new Label(
                    CurrencyFormatter.format(gp.current()) + " / " +
                    CurrencyFormatter.format(gp.target()));
            amounts.getStyleClass().add("dash-cat-amount");

            row.getChildren().addAll(name, bar, pctLabel, amounts);
            goalBox.getChildren().add(row);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private Label buildEmpty(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("dash-empty");
        lbl.setMaxWidth(Double.MAX_VALUE);
        lbl.setAlignment(Pos.CENTER);
        return lbl;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
