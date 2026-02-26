package org.example.service;

import org.example.config.HibernateUtil;
import org.example.model.enums.TransactionType;
import org.hibernate.Session;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

public class Analytics2Service {

    // ── Data Records ─────────────────────────────────────────────────

    public record HeatmapDay(LocalDate date, BigDecimal total, int intensity) {}
    public record ForecastData(BigDecimal projected, BigDecimal lastMonthTotal,
                               BigDecimal delta, String status) {}
    public record AnomalyEntry(String category, BigDecimal currentSum,
                               BigDecimal mean, BigDecimal sigma, double deviationPct) {}

    // ── Public API ───────────────────────────────────────────────────

    public List<HeatmapDay> getHeatmapData(YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end   = month.atEndOfMonth();

        Map<LocalDate, BigDecimal> dailyMap = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dailyMap.put(d, BigDecimal.ZERO);
        }

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            s.createQuery(
                    "SELECT t.date, SUM(t.amount) FROM Transaction t " +
                    "WHERE t.type = :type AND t.date >= :start AND t.date <= :end " +
                    "GROUP BY t.date",
                    Object[].class)
             .setParameter("type",  TransactionType.EXPENSE)
             .setParameter("start", start)
             .setParameter("end",   end)
             .list()
             .forEach(row -> dailyMap.put((LocalDate) row[0], (BigDecimal) row[1]));
        }

        List<BigDecimal> nonZero = dailyMap.values().stream()
                .filter(v -> v.compareTo(BigDecimal.ZERO) > 0)
                .sorted()
                .toList();

        List<HeatmapDay> result = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> e : dailyMap.entrySet()) {
            int intensity = (e.getValue().compareTo(BigDecimal.ZERO) > 0 && !nonZero.isEmpty())
                    ? computeIntensity(e.getValue(), nonZero) : 0;
            result.add(new HeatmapDay(e.getKey(), e.getValue(), intensity));
        }
        return result;
    }

    public ForecastData getForecast(YearMonth month) {
        YearMonth prevMonth   = month.minusMonths(1);
        LocalDate prevStart   = prevMonth.atDay(1);
        LocalDate prevEnd     = prevMonth.atEndOfMonth();
        LocalDate currStart   = month.atDay(1);
        LocalDate today       = LocalDate.now();

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            BigDecimal lastMonthTotal = querySum(s, prevStart, prevEnd);
            BigDecimal spentSoFar     = querySum(s, currStart, today);

            int prevDays      = prevMonth.lengthOfMonth();
            int remainingDays = Math.max(0, month.atEndOfMonth().getDayOfMonth() - today.getDayOfMonth());

            BigDecimal avgDaily = lastMonthTotal.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : lastMonthTotal.divide(BigDecimal.valueOf(prevDays), 2, RoundingMode.HALF_UP);

            BigDecimal projected = spentSoFar.add(
                    avgDaily.multiply(BigDecimal.valueOf(remainingDays))
                            .setScale(2, RoundingMode.HALF_UP));

            BigDecimal delta = projected.subtract(lastMonthTotal);

            String status;
            if (projected.compareTo(lastMonthTotal) <= 0) {
                status = "ok";
            } else if (lastMonthTotal.compareTo(BigDecimal.ZERO) == 0) {
                status = "warn";
            } else {
                double pct = delta.divide(lastMonthTotal, 4, RoundingMode.HALF_UP).doubleValue();
                status = pct <= 0.25 ? "warn" : "danger";
            }

            return new ForecastData(projected, lastMonthTotal, delta, status);
        }
    }

    public List<AnomalyEntry> detectAnomalies(YearMonth month) {
        return List.of();
    }

    // ── Private helpers ──────────────────────────────────────────────

    private int computeIntensity(BigDecimal value, List<BigDecimal> sortedNonZero) {
        int size = sortedNonZero.size();
        double q1 = sortedNonZero.get(Math.max(0, size / 4 - (size % 4 == 0 ? 1 : 0))).doubleValue();
        double q2 = sortedNonZero.get(size / 2).doubleValue();
        double q3 = sortedNonZero.get(Math.min(size - 1, size * 3 / 4)).doubleValue();
        double v  = value.doubleValue();
        if (v <= q1) return 1;
        if (v <= q2) return 2;
        if (v <= q3) return 3;
        return 4;
    }

    BigDecimal querySum(Session s, LocalDate start, LocalDate end) {
        BigDecimal r = s.createQuery(
                "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                "WHERE t.type = :type AND t.date >= :start AND t.date <= :end",
                BigDecimal.class)
            .setParameter("type",  TransactionType.EXPENSE)
            .setParameter("start", start)
            .setParameter("end",   end)
            .uniqueResult();
        return r == null ? BigDecimal.ZERO : r;
    }
}
