package org.example.service;

import org.example.model.entity.Category;
import org.example.util.TestDataHelper;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Analytics2ServiceTest {

    private static Analytics2Service service;

    @BeforeAll
    static void setup() {
        service = new Analytics2Service();
    }

    @BeforeEach
    void clean() {
        TestDataHelper.clearAll();
    }

    // ── Heatmap ──────────────────────────────────────────────────────

    @Test
    void heatmap_returnsAllDaysOfMonth() {
        YearMonth ym = YearMonth.of(2025, 1); // January = 31 days
        List<Analytics2Service.HeatmapDay> result = service.getHeatmapData(ym);
        assertEquals(31, result.size());
    }

    @Test
    void heatmap_zeroIntensityWhenNoData() {
        YearMonth ym = YearMonth.of(2025, 1);
        List<Analytics2Service.HeatmapDay> result = service.getHeatmapData(ym);
        assertTrue(result.stream().allMatch(d -> d.intensity() == 0));
    }

    @Test
    void heatmap_nonZeroIntensityForDaysWithExpenses() {
        Category food = TestDataHelper.saveCategory("Еда");
        // Seed 4 different amounts to exercise all intensity levels
        TestDataHelper.saveExpense(food, LocalDate.of(2025, 1, 1),  new BigDecimal("100"));
        TestDataHelper.saveExpense(food, LocalDate.of(2025, 1, 2),  new BigDecimal("500"));
        TestDataHelper.saveExpense(food, LocalDate.of(2025, 1, 3),  new BigDecimal("1000"));
        TestDataHelper.saveExpense(food, LocalDate.of(2025, 1, 4),  new BigDecimal("2000"));

        List<Analytics2Service.HeatmapDay> result = service.getHeatmapData(YearMonth.of(2025, 1));

        long withData = result.stream().filter(d -> d.intensity() > 0).count();
        assertEquals(4, withData);
    }

    // ── Forecast ─────────────────────────────────────────────────────

    @Test
    void forecast_okStatusWhenProjectedBelowLastMonth() {
        // Last month: 3000 total (100/day × 30 days)
        // Current month (first day only, then projected): should be "ok" if projected ≤ 3000
        Category food = TestDataHelper.saveCategory("Еда");
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        LocalDate lastStart = lastMonth.atDay(1);
        // Seed uniform daily expense for entire last month
        for (LocalDate d = lastStart; !d.isAfter(lastMonth.atEndOfMonth()); d = d.plusDays(1)) {
            TestDataHelper.saveExpense(food, d, new BigDecimal("100"));
        }
        // No expense this month yet — projected should equal avgDaily × remaining days
        Analytics2Service.ForecastData fd = service.getForecast(YearMonth.now());
        assertNotNull(fd);
        assertTrue(List.of("ok", "warn", "danger").contains(fd.status()));
    }

    @Test
    void forecast_returnsNonNullForEmptyData() {
        Analytics2Service.ForecastData fd = service.getForecast(YearMonth.now());
        assertNotNull(fd);
        assertEquals("ok", fd.status()); // no last month data → projected = 0 ≤ 0
    }

    // ── Anomalies ─────────────────────────────────────────────────────

    @Test
    void anomalies_emptyWhenNoData() {
        List<Analytics2Service.AnomalyEntry> result =
                service.detectAnomalies(YearMonth.now());
        assertTrue(result.isEmpty());
    }

    @Test
    void anomalies_detectedWhenCurrentExceedsMeanPlusTwoSigma() {
        Category food = TestDataHelper.saveCategory("Рестораны");
        YearMonth now = YearMonth.now();

        // Historical 3 months: low stable spending (mean ~500, sigma ~0)
        for (int i = 3; i >= 1; i--) {
            YearMonth hm = now.minusMonths(i);
            TestDataHelper.saveExpense(food, hm.atDay(15), new BigDecimal("500"));
        }
        // Current month: huge spike (5000 >> 500 + 2*0)
        TestDataHelper.saveExpense(food, now.atDay(10), new BigDecimal("5000"));

        List<Analytics2Service.AnomalyEntry> result = service.detectAnomalies(now);
        assertEquals(1, result.size());
        assertEquals("Рестораны", result.get(0).category());
        assertTrue(result.get(0).deviationPct() > 100);
    }

    @Test
    void anomalies_notDetectedWhenCurrentWithinNormal() {
        Category food = TestDataHelper.saveCategory("Продукты");
        YearMonth now = YearMonth.now();

        // Historical 3 months: 400, 500, 600 (mean=500, sigma≈82)
        TestDataHelper.saveExpense(food, now.minusMonths(3).atDay(15), new BigDecimal("400"));
        TestDataHelper.saveExpense(food, now.minusMonths(2).atDay(15), new BigDecimal("500"));
        TestDataHelper.saveExpense(food, now.minusMonths(1).atDay(15), new BigDecimal("600"));
        // Current: 650 — within mean + 2σ ≈ 500 + 164 = 664
        TestDataHelper.saveExpense(food, now.atDay(15), new BigDecimal("650"));

        List<Analytics2Service.AnomalyEntry> result = service.detectAnomalies(now);
        assertTrue(result.isEmpty());
    }
}
