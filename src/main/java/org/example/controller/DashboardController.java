package org.example.controller;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;
import org.example.model.entity.SavingsGoal;
import org.example.model.entity.Transaction;
import org.example.model.enums.TransactionType;
import org.example.service.DashboardService;
import org.example.service.DashboardService.CategoryAmount;
import org.example.service.DashboardService.DayData;
import org.example.service.DashboardService.MonthStats;
import org.example.util.CurrencyFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

public class DashboardController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yy");

    private static final String[] DONUT_COLORS = {
        "#F472B6", "#FF9500", "#FFD60A", "#30D158",
        "#0A84FF", "#5E5CE6", "#BF5AF2", "#FF6B35"
    };

    //FXML nodes
    @FXML private Label monthLabel;
    @FXML private Label balanceValue;
    @FXML private Label incomeValue;
    @FXML private Label expenseValue;
    @FXML private Label goalsCountValue;

    @FXML private VBox lineChartBox;
    @FXML private VBox pieChartBox;
    @FXML private VBox topCategoriesBox;
    @FXML private VBox recentTxBox;
    @FXML private VBox goalsSection;
    @FXML private HBox goalsRow;

    private DashboardService service;

    @FXML
    public void initialize() {
        service = new DashboardService();

        LocalDate now = LocalDate.now();
        String month = now.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru", "RU"));
        monthLabel.setText(capitalize(month) + " " + now.getYear());

        refresh();
    }

    @FXML
    public void refresh() {
        updateMetrics();
        updateLineChart();
        updatePieChart();
        updateTopCategories();
        updateRecentTransactions();
        updateGoalsMini();
    }

    private void updateMetrics() {
        MonthStats stats = service.getMonthStats();
        BigDecimal balance = stats.balance();

        balanceValue.setText(CurrencyFormatter.format(balance));
        balanceValue.getStyleClass().removeAll("dash-positive", "dash-negative");
        balanceValue.getStyleClass().add(
                balance.compareTo(BigDecimal.ZERO) >= 0 ? "dash-positive" : "dash-negative");

        incomeValue.setText(CurrencyFormatter.format(stats.income()));
        expenseValue.setText(CurrencyFormatter.format(stats.expense()));
        goalsCountValue.setText(String.valueOf(service.countActiveGoals()));
    }


    private void updateLineChart() {
        lineChartBox.getChildren().clear();

        List<DayData> data = service.getDailyData();
        boolean hasData = data.stream().anyMatch(d ->
                d.income().compareTo(BigDecimal.ZERO) > 0 ||
                d.expense().compareTo(BigDecimal.ZERO) > 0);

        if (!hasData) {
            lineChartBox.getChildren().add(buildEmpty("Нет транзакций в этом месяце"));
            return;
        }

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setTickLabelRotation(-90);
        xAxis.setTickLabelGap(2);

        NumberAxis yAxis = new NumberAxis();
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

        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(true);
        chart.getStyleClass().add("dash-line-chart");
        VBox.setVgrow(chart, Priority.ALWAYS);
        chart.setMaxHeight(Double.MAX_VALUE);

        XYChart.Series<String, Number> incomeSeries  = new XYChart.Series<>();
        incomeSeries.setName("Доходы");
        XYChart.Series<String, Number> expenseSeries = new XYChart.Series<>();
        expenseSeries.setName("Расходы");

        for (DayData d : data) {
            String label = String.valueOf(d.date().getDayOfMonth());
            incomeSeries.getData().add(new XYChart.Data<>(label, d.income()));
            expenseSeries.getData().add(new XYChart.Data<>(label, d.expense()));
        }

        chart.getData().addAll(incomeSeries, expenseSeries);
        lineChartBox.getChildren().add(chart);
    }

    private void updatePieChart() {
        pieChartBox.getChildren().clear();

        List<CategoryAmount> cats = service.getExpenseByCategory();
        if (cats.isEmpty()) {
            pieChartBox.getChildren().add(buildEmpty("Нет расходов в этом месяце"));
            return;
        }

        BigDecimal total = cats.stream()
                .map(CategoryAmount::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double totalVal = total.doubleValue();
        double[] values = cats.stream()
                .mapToDouble(ca -> ca.amount().doubleValue()).toArray();

        // Arc pane: arcs are drawn here with absolute coordinates
        Pane arcPane = new Pane();
        arcPane.setMaxWidth(Double.MAX_VALUE);
        arcPane.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(arcPane, Priority.ALWAYS);

        // Center labels
        Label amountLabel = new Label(CurrencyFormatter.format(total));
        amountLabel.getStyleClass().add("pie-center-amount");
        amountLabel.setMouseTransparent(true);

        Label captionLabel = new Label("расходы");
        captionLabel.getStyleClass().add("pie-center-caption");
        captionLabel.setMouseTransparent(true);

        VBox centerInfo = new VBox(2, amountLabel, captionLabel);
        centerInfo.setAlignment(Pos.CENTER);
        centerInfo.setMouseTransparent(true);

        StackPane donutStack = new StackPane(arcPane, centerInfo);
        VBox.setVgrow(donutStack, Priority.ALWAYS);
        donutStack.setMaxHeight(Double.MAX_VALUE);
        donutStack.setMinHeight(160);

        donutStack.widthProperty().addListener((obs, old, w) ->
                drawDonutArcs(arcPane, values, totalVal, w.doubleValue(), donutStack.getHeight()));
        donutStack.heightProperty().addListener((obs, old, h) ->
                drawDonutArcs(arcPane, values, totalVal, donutStack.getWidth(), h.doubleValue()));

        // Custom legend
        FlowPane legend = new FlowPane(10, 6);
        legend.setAlignment(Pos.CENTER);
        for (int i = 0; i < cats.size(); i++) {
            HBox item = new HBox(6);
            item.setAlignment(Pos.CENTER_LEFT);
            Circle dot = new Circle(5, Color.web(DONUT_COLORS[i % DONUT_COLORS.length]));
            Label name = new Label(cats.get(i).name());
            name.getStyleClass().add("dash-pie-legend-label");
            item.getChildren().addAll(dot, name);
            legend.getChildren().add(item);
        }

        VBox container = new VBox(8, donutStack, legend);
        VBox.setVgrow(container, Priority.ALWAYS);
        container.setMaxHeight(Double.MAX_VALUE);

        pieChartBox.getChildren().add(container);
    }

    private void drawDonutArcs(Pane arcPane, double[] values, double total,
                                double w, double h) {
        if (w < 10 || h < 10) return;
        arcPane.getChildren().clear();

        double size   = Math.min(w, h);
        double cx     = w / 2.0;
        double cy     = h / 2.0;
        double outerR = size * 0.44;
        double innerR = outerR * 0.60;
        double midR   = (outerR + innerR) / 2.0;
        double ringW  = outerR - innerR;
        double gap    = values.length > 1 ? 1.5 : 0.0;

        double startAngle = 90.0;

        for (int i = 0; i < values.length; i++) {
            double sweep  = (values[i] / total) * 360.0;
            double actual = sweep - gap;
            if (actual < 0.5) { startAngle -= sweep; continue; }

            Arc arc = new Arc();
            arc.setCenterX(cx);
            arc.setCenterY(cy);
            arc.setRadiusX(midR);
            arc.setRadiusY(midR);
            arc.setStartAngle(startAngle - gap / 2.0);
            arc.setLength(-actual);
            arc.setType(ArcType.OPEN);
            arc.setFill(Color.TRANSPARENT);
            arc.setStroke(Color.web(DONUT_COLORS[i % DONUT_COLORS.length]));
            arc.setStrokeWidth(ringW);
            arc.setStrokeLineCap(StrokeLineCap.BUTT);
            arc.setSmooth(true);

            arcPane.getChildren().add(arc);
            startAngle -= sweep;
        }
    }

    // ── Top Categories ───────────────────────────────────────────────

    private void updateTopCategories() {
        topCategoriesBox.getChildren().clear();

        List<CategoryAmount> cats = service.getTopExpenseCategories(5);
        if (cats.isEmpty()) {
            topCategoriesBox.getChildren().add(buildEmpty("Нет расходов в этом месяце"));
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
            bar.setPrefWidth(70);
            bar.getStyleClass().add("dash-cat-bar");

            Label amount = new Label(CurrencyFormatter.format(ca.amount()));
            amount.getStyleClass().add("dash-cat-amount");

            row.getChildren().addAll(name, bar, amount);
            topCategoriesBox.getChildren().add(row);
        }
    }

    // ── Recent Transactions ──────────────────────────────────────────

    private void updateRecentTransactions() {
        recentTxBox.getChildren().clear();

        List<Transaction> txs = service.getRecentTransactions(5);
        if (txs.isEmpty()) {
            recentTxBox.getChildren().add(buildEmpty("Нет транзакций"));
            return;
        }

        for (Transaction tx : txs) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("dash-tx-row");

            VBox left = new VBox(2);
            HBox.setHgrow(left, Priority.ALWAYS);

            Label cat = new Label(tx.getCategory().getName());
            cat.getStyleClass().add("dash-tx-cat");

            Label date = new Label(tx.getDate().format(DATE_FMT));
            date.getStyleClass().add("dash-tx-date");
            left.getChildren().addAll(cat, date);

            boolean isIncome = tx.getType() == TransactionType.INCOME;
            Label amountLabel = new Label(
                    (isIncome ? "+" : "−") + " " + CurrencyFormatter.format(tx.getAmount()));
            amountLabel.getStyleClass().add(isIncome ? "dash-tx-income" : "dash-tx-expense");

            row.getChildren().addAll(left, amountLabel);
            recentTxBox.getChildren().add(row);
        }
    }

    // ── Mini Goal Cards ──────────────────────────────────────────────

    private void updateGoalsMini() {
        goalsRow.getChildren().clear();

        List<SavingsGoal> goals = service.getTopActiveGoals(3);
        goalsSection.setVisible(!goals.isEmpty());
        goalsSection.setManaged(!goals.isEmpty());

        goals.forEach(g -> {
            HBox.setHgrow(buildMiniGoalCard(g), Priority.ALWAYS);
            goalsRow.getChildren().add(buildMiniGoalCard(g));
        });
    }

    private VBox buildMiniGoalCard(SavingsGoal goal) {
        VBox card = new VBox(8);
        card.getStyleClass().add("dash-goal-card");
        HBox.setHgrow(card, Priority.ALWAYS);

        Label name = new Label(goal.getName());
        name.getStyleClass().add("dash-goal-name");
        name.setWrapText(true);

        double ratio = goal.getTargetAmount().compareTo(BigDecimal.ZERO) == 0 ? 0 :
                goal.getCurrentAmount()
                    .divide(goal.getTargetAmount(), 4, RoundingMode.HALF_UP)
                    .doubleValue();

        ProgressBar bar = new ProgressBar(Math.min(ratio, 1.0));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("goal-progress-bar");
        if (ratio >= 0.75) bar.getStyleClass().add("goal-progress-high");

        int pct = (int) Math.min(ratio * 100, 100);
        HBox amounts = new HBox();
        amounts.setAlignment(Pos.CENTER_LEFT);
        Label cur = new Label(CurrencyFormatter.format(goal.getCurrentAmount()));
        cur.getStyleClass().add("dash-goal-current");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label pctLabel = new Label(pct + "%");
        pctLabel.getStyleClass().add("goal-percent");
        amounts.getChildren().addAll(cur, sp, pctLabel);

        Label target = new Label("из " + CurrencyFormatter.format(goal.getTargetAmount()));
        target.getStyleClass().add("dash-goal-target");

        card.getChildren().addAll(name, bar, amounts, target);

        if (goal.getDeadline() != null) {
            Label dl = new Label("до " + goal.getDeadline().format(DATE_FMT));
            dl.getStyleClass().add("goal-deadline");
            if (goal.getDeadline().isBefore(LocalDate.now()))
                dl.getStyleClass().add("goal-deadline-overdue");
            card.getChildren().add(dl);
        }

        return card;
    }

    // ── Helpers ──────────────────────────────────────────────────────

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
