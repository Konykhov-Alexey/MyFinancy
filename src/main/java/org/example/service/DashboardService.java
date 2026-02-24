package org.example.service;

import org.example.config.HibernateUtil;
import org.example.model.entity.SavingsGoal;
import org.example.model.entity.Transaction;
import org.example.model.enums.GoalStatus;
import org.example.model.enums.TransactionType;
import org.hibernate.Session;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public class DashboardService {

    // ── Data Records ─────────────────────────────────────────────────

    public record MonthStats(BigDecimal income, BigDecimal expense) {
        public BigDecimal balance() { return income.subtract(expense); }
    }

    public record CategoryAmount(String name, BigDecimal amount) {}

    public record DayData(LocalDate date, BigDecimal income, BigDecimal expense) {}

    // ── Date helpers ─────────────────────────────────────────────────

    private static LocalDate monthStart() { return LocalDate.now().withDayOfMonth(1); }
    private static LocalDate monthEnd()   { return monthStart().plusMonths(1); }

    // ── Public API ───────────────────────────────────────────────────

    /** Суммарные доходы и расходы за текущий месяц. */
    public MonthStats getMonthStats() {
        LocalDate start = monthStart();
        LocalDate end   = monthEnd();
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            BigDecimal income  = sumByType(s, TransactionType.INCOME,  start, end);
            BigDecimal expense = sumByType(s, TransactionType.EXPENSE, start, end);
            return new MonthStats(income, expense);
        }
    }

    /** Топ N категорий расходов за текущий месяц. */
    public List<CategoryAmount> getTopExpenseCategories(int limit) {
        LocalDate start = monthStart(), end = monthEnd();
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            return s.createQuery(
                            "SELECT t.category.name, SUM(t.amount) FROM Transaction t " +
                            "WHERE t.type = :type AND t.date >= :start AND t.date < :end " +
                            "GROUP BY t.category.name ORDER BY SUM(t.amount) DESC",
                            Object[].class)
                    .setParameter("type",  TransactionType.EXPENSE)
                    .setParameter("start", start)
                    .setParameter("end",   end)
                    .setMaxResults(limit)
                    .list()
                    .stream()
                    .map(row -> new CategoryAmount((String) row[0], (BigDecimal) row[1]))
                    .toList();
        }
    }

    /** Все расходы по категориям за текущий месяц (для PieChart, max 8 срезов). */
    public List<CategoryAmount> getExpenseByCategory() {
        return getTopExpenseCategories(8);
    }

    /**
     * Данные по каждому дню текущего месяца.
     * Возвращает все дни; для дней без транзакций income/expense = 0.
     */
    public List<DayData> getDailyData() {
        LocalDate start = monthStart();
        LocalDate end   = monthEnd();
        Map<LocalDate, BigDecimal> incomeMap  = sumByDay(TransactionType.INCOME,  start, end);
        Map<LocalDate, BigDecimal> expenseMap = sumByDay(TransactionType.EXPENSE, start, end);

        List<DayData> result = new ArrayList<>();
        for (LocalDate d = start; d.isBefore(end); d = d.plusDays(1)) {
            result.add(new DayData(
                    d,
                    incomeMap.getOrDefault(d,  BigDecimal.ZERO),
                    expenseMap.getOrDefault(d, BigDecimal.ZERO)));
        }
        return result;
    }

    /** Топ N активных целей по ближайшему дедлайну (NULL-дедлайн идёт последним). */
    public List<SavingsGoal> getTopActiveGoals(int limit) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            return s.createQuery(
                            "FROM SavingsGoal WHERE status = :status " +
                            "ORDER BY CASE WHEN deadline IS NULL THEN 1 ELSE 0 END, deadline, name",
                            SavingsGoal.class)
                    .setParameter("status", GoalStatus.ACTIVE)
                    .setMaxResults(limit)
                    .list();
        }
    }

    /** Последние N транзакций по всей базе. */
    public List<Transaction> getRecentTransactions(int limit) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            return s.createQuery(
                            "SELECT t FROM Transaction t JOIN FETCH t.category " +
                            "ORDER BY t.date DESC, t.id DESC",
                            Transaction.class)
                    .setMaxResults(limit)
                    .list();
        }
    }

    /** Количество активных целей. */
    public long countActiveGoals() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Long n = s.createQuery(
                            "SELECT COUNT(g) FROM SavingsGoal g WHERE g.status = :status",
                            Long.class)
                    .setParameter("status", GoalStatus.ACTIVE)
                    .uniqueResult();
            return n != null ? n : 0L;
        }
    }

    // ── Private helpers ──────────────────────────────────────────────

    private BigDecimal sumByType(Session s, TransactionType type,
                                 LocalDate start, LocalDate end) {
        BigDecimal r = s.createQuery(
                        "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                        "WHERE t.type = :type AND t.date >= :start AND t.date < :end",
                        BigDecimal.class)
                .setParameter("type",  type)
                .setParameter("start", start)
                .setParameter("end",   end)
                .uniqueResult();
        return r != null ? r : BigDecimal.ZERO;
    }

    private Map<LocalDate, BigDecimal> sumByDay(TransactionType type,
                                                LocalDate start, LocalDate end) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Map<LocalDate, BigDecimal> map = new LinkedHashMap<>();
            s.createQuery(
                            "SELECT t.date, SUM(t.amount) FROM Transaction t " +
                            "WHERE t.type = :type AND t.date >= :start AND t.date < :end " +
                            "GROUP BY t.date ORDER BY t.date",
                            Object[].class)
                    .setParameter("type",  type)
                    .setParameter("start", start)
                    .setParameter("end",   end)
                    .list()
                    .forEach(row -> map.put((LocalDate) row[0], (BigDecimal) row[1]));
            return map;
        }
    }
}
