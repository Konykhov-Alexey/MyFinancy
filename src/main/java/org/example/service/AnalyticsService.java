package org.example.service;

import org.example.config.HibernateUtil;
import org.example.model.entity.SavingsGoal;
import org.example.model.enums.GoalStatus;
import org.example.model.enums.TransactionType;
import org.hibernate.Session;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class AnalyticsService {

    // ── Data Records ─────────────────────────────────────────────────

    public record MonthBar(YearMonth month, BigDecimal income, BigDecimal expense) {}

    public record CategoryAmount(String name, BigDecimal amount) {}

    public record Averages(BigDecimal daily, BigDecimal weekly, BigDecimal monthly) {}

    public record GoalProgress(String name, BigDecimal current, BigDecimal target, double ratio) {}

    // ── Public API ───────────────────────────────────────────────────

    /** Income/expense grouped by month for the last {@code months} months (incl. current). */
    public List<MonthBar> getMonthlyComparison(int months) {
        LocalDate start = YearMonth.now().minusMonths(months - 1).atDay(1);
        LocalDate end   = YearMonth.now().atEndOfMonth().plusDays(1);

        Map<YearMonth, BigDecimal[]> map = new LinkedHashMap<>();
        for (int i = months - 1; i >= 0; i--) {
            map.put(YearMonth.now().minusMonths(i), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            fillByMonth(s, TransactionType.INCOME,  start, end, map, 0);
            fillByMonth(s, TransactionType.EXPENSE, start, end, map, 1);
        }

        List<MonthBar> result = new ArrayList<>();
        map.forEach((ym, arr) -> result.add(new MonthBar(ym, arr[0], arr[1])));
        return result;
    }

    /** Top {@code limit} expense categories for [from, to] inclusive. */
    public List<CategoryAmount> getTopCategories(LocalDate from, LocalDate to, int limit) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            return s.createQuery(
                    "SELECT t.category.name, SUM(t.amount) FROM Transaction t " +
                    "WHERE t.type = :type AND t.date >= :start AND t.date <= :end " +
                    "GROUP BY t.category.name ORDER BY SUM(t.amount) DESC",
                    Object[].class)
                 .setParameter("type",  TransactionType.EXPENSE)
                 .setParameter("start", from)
                 .setParameter("end",   to)
                 .setMaxResults(limit)
                 .list()
                 .stream()
                 .map(row -> new CategoryAmount((String) row[0], (BigDecimal) row[1]))
                 .toList();
        }
    }

    /** Average daily / weekly / monthly expense for [from, to]. */
    public Averages getExpenseAverages(LocalDate from, LocalDate to) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            BigDecimal total = s.createQuery(
                    "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                    "WHERE t.type = :type AND t.date >= :start AND t.date <= :end",
                    BigDecimal.class)
                 .setParameter("type",  TransactionType.EXPENSE)
                 .setParameter("start", from)
                 .setParameter("end",   to)
                 .uniqueResult();

            if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
                return new Averages(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            }

            long days = Math.max(1, ChronoUnit.DAYS.between(from, to) + 1);
            BigDecimal daily  = total.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
            BigDecimal weekly = daily.multiply(BigDecimal.valueOf(7)).setScale(2, RoundingMode.HALF_UP);
            long months = Math.max(1,
                    ChronoUnit.MONTHS.between(from.withDayOfMonth(1), to.withDayOfMonth(1)) + 1);
            BigDecimal monthly = total.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);

            return new Averages(daily, weekly, monthly);
        }
    }

    /** Name of the top expense category for [from, to], or "—" if no data. */
    public String getTopCategoryName(LocalDate from, LocalDate to) {
        List<CategoryAmount> top = getTopCategories(from, to, 1);
        return top.isEmpty() ? "—" : top.get(0).name();
    }

    /** Progress of all active savings goals ordered by name. */
    public List<GoalProgress> getGoalProgress() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            return s.createQuery(
                    "FROM SavingsGoal WHERE status = :status ORDER BY name",
                    SavingsGoal.class)
                 .setParameter("status", GoalStatus.ACTIVE)
                 .list()
                 .stream()
                 .map(g -> {
                     double ratio = g.getTargetAmount().compareTo(BigDecimal.ZERO) == 0 ? 0.0 :
                             g.getCurrentAmount()
                              .divide(g.getTargetAmount(), 4, RoundingMode.HALF_UP)
                              .doubleValue();
                     return new GoalProgress(g.getName(), g.getCurrentAmount(), g.getTargetAmount(), ratio);
                 })
                 .toList();
        }
    }

    // ── Private helpers ──────────────────────────────────────────────

    private void fillByMonth(Session s, TransactionType type, LocalDate start, LocalDate end,
                              Map<YearMonth, BigDecimal[]> map, int idx) {
        s.createQuery(
                "SELECT EXTRACT(YEAR FROM t.date), EXTRACT(MONTH FROM t.date), SUM(t.amount) " +
                "FROM Transaction t " +
                "WHERE t.type = :type AND t.date >= :start AND t.date < :end " +
                "GROUP BY EXTRACT(YEAR FROM t.date), EXTRACT(MONTH FROM t.date)",
                Object[].class)
         .setParameter("type",  type)
         .setParameter("start", start)
         .setParameter("end",   end)
         .list()
         .forEach(row -> {
             YearMonth ym = YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
             if (map.containsKey(ym)) map.get(ym)[idx] = (BigDecimal) row[2];
         });
    }
}
