# Analytics 2.0 — Phase 9 Design

**Date:** 2026-02-26
**Branch:** rework-analytics-function

---

## Overview

Three new analytical widgets added to the bottom of the existing Analytics screen. No new entities — only a new service class. All widgets are called from `refresh()` and always use `YearMonth.now()`.

---

## Architecture

**New file:** `service/Analytics2Service.java`
Three public methods, three nested records. Follows the same pattern as `AnalyticsService`.

**Modified files:**
- `controller/AnalyticsController.java` — new field `service2`, three new `update*()` methods
- `fxml/analytics.fxml` — three new cards at the bottom of ScrollPane VBox
- `css/styles.css` — new section `/* ANALYTICS 2.0 — Phase 9 */`

---

## Data Records

```java
record HeatmapDay(LocalDate date, BigDecimal total, int intensity) {}
// intensity: 0=no data, 1–4 by quartile of month sums

record ForecastData(BigDecimal projected, BigDecimal lastMonthTotal,
                    BigDecimal delta, String status) {}
// status: "ok" | "warn" | "danger"
// ok = projected ≤ lastMonthTotal
// warn = delta ≤ 25% of lastMonthTotal
// danger = delta > 25%

record AnomalyEntry(String category, BigDecimal currentSum,
                    BigDecimal mean, BigDecimal sigma, double deviationPct) {}
// deviationPct = (currentSum - mean) / mean * 100
// Only upward anomalies: currentSum > mean + 2σ
```

---

## Service Methods

### `getHeatmapData(YearMonth month)`
- HQL GROUP BY `t.date` for EXPENSE within the given month
- Intensity calculated by quartiles of non-zero daily sums:
  - 0 = no data, 1 = Q1, 2 = Q2, 3 = Q3, 4 = Q4 (max)
- Returns a `List<HeatmapDay>` for every calendar day of the month

### `getForecast(YearMonth month)`
1. `lastMonthTotal` = SUM(EXPENSE) for previous month
2. `avgDaily = lastMonthTotal / days in previous month`
3. `remainingDays` = days from tomorrow to end of current month
4. `spentSoFar` = SUM(EXPENSE) from 1st of current month to today
5. `projected = spentSoFar + avgDaily × remainingDays`
6. `delta = projected - lastMonthTotal`
7. Status thresholds:
   - `ok` if `projected ≤ lastMonthTotal`
   - `warn` if `delta ≤ 25%` of lastMonthTotal
   - `danger` if `delta > 25%` of lastMonthTotal

### `detectAnomalies(YearMonth month)`
1. Query EXPENSE by category for the 3 previous months (excluding current)
2. Compute `mean` and `σ` per category in Java
3. Query EXPENSE by category for current month
4. Anomaly condition: `currentSum > mean + 2σ`
5. Return sorted by `deviationPct` descending
6. Only upward anomalies included

---

## Controller Changes

New fields in `AnalyticsController`:
```java
@FXML private VBox heatmapBox;
@FXML private VBox forecastBox;
@FXML private VBox anomalyBox;
private Analytics2Service service2;
```

New methods:
- `updateHeatmap()` — builds `GridPane` (7 cols × 6 rows) programmatically; adds `Tooltip` with date + amount to each cell
- `updateForecast()` — builds card with projected amount, status badge, and delta vs last month
- `updateAnomalies()` — builds list rows with anomaly badges; hides section if no anomalies
- All called from `refresh()` with `YearMonth.now()`

---

## FXML Layout (analytics.fxml additions)

Three new cards appended after `goalSection`:

```
┌─────────────────────────────────────────────┐
│ ТЕПЛОВАЯ КАРТА РАСХОДОВ (текущий месяц)     │
│  Пн Вт Ср Чт Пт Сб Вс                      │
│  GridPane 7×6, cells 28×28, Tooltip on each │
└─────────────────────────────────────────────┘
┌─────────────────────────────────────────────┐
│ ПРОГНОЗ РАСХОДОВ НА МЕСЯЦ                   │
│  ≈ 45 200 ₽  [warn]  +12% vs прошлый месяц │
└─────────────────────────────────────────────┘
┌─────────────────────────────────────────────┐
│ АНОМАЛИИ РАСХОДОВ                           │
│  category | current | mean | deviation%     │
│  (hidden if no anomalies)                   │
└─────────────────────────────────────────────┘
```

---

## CSS

New section `/* ANALYTICS 2.0 — Phase 9 */`:

**Heatmap:**
- `.heatmap-cell` — 28×28px, border-radius 4, cursor pointer
- `.heatmap-intensity-0` — `#1C1C20`
- `.heatmap-intensity-1` — `#0D3D2A`
- `.heatmap-intensity-2` — `#0A5C3A`
- `.heatmap-intensity-3` — `#0D7A4A`
- `.heatmap-intensity-4` — `#10B981`
- `.heatmap-day-label` — `#6B7280`, 10px

**Forecast:**
- `.forecast-value` — 24px bold, `#FFFFFF`
- `.forecast-ok` — `#10B981`
- `.forecast-warn` — `#F59E0B`
- `.forecast-danger` — `#EF4444`
- `.forecast-meta` — `#9CA3AF`, 12px

**Anomalies:**
- `.anomaly-row` — border-bottom `#1C1C24`, padding 8px 0
- `.anomaly-badge` — pill, border-radius 6, 11px bold
- `.anomaly-badge-high` — bg `rgba(239,68,68,0.15)`, text `#EF4444`
- `.anomaly-cat-name` — `#FFFFFF`, 13px
- `.anomaly-meta` — `#6B7280`, 11px

---

## Implementation Order

1. `Analytics2Service` (getHeatmapData → getForecast → detectAnomalies)
2. CSS classes
3. `buildHeatmapGrid()` + FXML card
4. `updateForecast()` + FXML card
5. `updateAnomalies()` + FXML card
6. Wire all into `refresh()`

---

## Verification

```bash
export JAVA_HOME=~/.jdks/corretto-25.0.2
mvn clean compile
mvn javafx:run
```

Open Analytics screen → scroll down → heatmap for current month, forecast card, anomaly list visible.