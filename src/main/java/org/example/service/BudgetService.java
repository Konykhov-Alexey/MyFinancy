package org.example.service;

import org.example.config.HibernateUtil;
import org.example.model.entity.BudgetGroup;
import org.example.model.entity.Category;
import org.example.model.enums.TransactionType;
import org.example.repository.BudgetGroupRepository;
import org.example.repository.CategoryRepository;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

public class BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);

    public record GroupBudget(
            BudgetGroup group,
            BigDecimal  planned,    // monthlyIncome × percentage / 100
            BigDecimal  spent,      // Σ EXPENSE транзакций за месяц по категориям группы
            BigDecimal  remaining,  // planned − spent
            double      ratio       // spent / planned (0.0–1.0+)
    ) {}

    /** Сводный план на месяц. */
    public record MonthlyPlan(
            BigDecimal        income,       // Σ INCOME за месяц
            List<GroupBudget> groups,       // исполнение по каждой группе
            BigDecimal        unallocated   // income − Σ planned (если проценты < 100%)
    ) {}

    // ── Dependencies ─────────────────────────────────────────────────

    private final BudgetGroupRepository groupRepo;
    private final CategoryRepository    categoryRepo;

    public BudgetService(BudgetGroupRepository groupRepo, CategoryRepository categoryRepo) {
        this.groupRepo    = groupRepo;
        this.categoryRepo = categoryRepo;
    }

    // ── Groups CRUD ──────────────────────────────────────────────────

    public List<BudgetGroup> getGroups() {
        return groupRepo.findAll();
    }

    public BudgetGroup createGroup(String name, BigDecimal percentage, String color) {
        validatePercentage(percentage);
        validateTotalPercentage(percentage, null);

        List<BudgetGroup> existing = groupRepo.findAll();
        int nextOrder = existing.stream().mapToInt(BudgetGroup::getSortOrder).max().orElse(0) + 1;

        BudgetGroup group = BudgetGroup.builder()
                .name(name.trim())
                .percentage(percentage)
                .color(color)
                .sortOrder(nextOrder)
                .build();
        return groupRepo.save(group);
    }

    public BudgetGroup updateGroup(BudgetGroup group) {
        validatePercentage(group.getPercentage());
        validateTotalPercentage(group.getPercentage(), group.getId());
        return groupRepo.save(group);
    }

    public void deleteGroup(BudgetGroup group) {
        groupRepo.delete(group);
    }

    // ── Category assignment ──────────────────────────────────────────

    /** Привязывает категорию к группе (group=null — отвязать). */
    public void assignCategory(Category category, BudgetGroup group) {
        category.setGroup(group);
        categoryRepo.save(category);
    }

    // ── Budget Plan ──────────────────────────────────────────────────

    /** Суммарный доход за месяц. */
    public BigDecimal getMonthlyIncome(YearMonth ym) {
        var start = ym.atDay(1);
        var end   = ym.atEndOfMonth().plusDays(1);
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            BigDecimal r = s.createQuery(
                    "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                    "WHERE t.type = :type AND t.date >= :start AND t.date < :end",
                    BigDecimal.class)
                    .setParameter("type",  TransactionType.INCOME)
                    .setParameter("start", start)
                    .setParameter("end",   end)
                    .uniqueResult();
            return r != null ? r : BigDecimal.ZERO;
        }
    }

    /** Ключевой метод: план и исполнение за месяц. */
    public MonthlyPlan getMonthlyPlan(YearMonth ym) {
        BigDecimal income = getMonthlyIncome(ym);
        List<BudgetGroup> groups = groupRepo.findAll();

        var start = ym.atDay(1);
        var end   = ym.atEndOfMonth().plusDays(1);

        BigDecimal totalPlanned = BigDecimal.ZERO;
        List<GroupBudget> budgets = new java.util.ArrayList<>();

        for (BudgetGroup g : groups) {
            BigDecimal planned = income.multiply(g.getPercentage())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal spent   = getSpentByGroup(g, start, end);
            BigDecimal remaining = planned.subtract(spent);
            double ratio = planned.compareTo(BigDecimal.ZERO) == 0 ? 0.0 :
                    spent.divide(planned, 4, RoundingMode.HALF_UP).doubleValue();

            budgets.add(new GroupBudget(g, planned, spent, remaining, ratio));
            totalPlanned = totalPlanned.add(planned);
        }

        BigDecimal unallocated = income.subtract(totalPlanned);
        return new MonthlyPlan(income, budgets, unallocated);
    }

    /** Суммарный процент всех групп (для индикатора распределения). */
    public BigDecimal getTotalPercentage() {
        return groupRepo.findAll().stream()
                .map(BudgetGroup::getPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ── Private helpers ──────────────────────────────────────────────

    private BigDecimal getSpentByGroup(BudgetGroup group,
                                       java.time.LocalDate start,
                                       java.time.LocalDate end) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            BigDecimal r = s.createQuery(
                    "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                    "WHERE t.type = :type AND t.category.group = :group " +
                    "AND t.date >= :start AND t.date < :end",
                    BigDecimal.class)
                    .setParameter("type",  TransactionType.EXPENSE)
                    .setParameter("group", group)
                    .setParameter("start", start)
                    .setParameter("end",   end)
                    .uniqueResult();
            return r != null ? r : BigDecimal.ZERO;
        }
    }

    private void validatePercentage(BigDecimal pct) {
        if (pct == null || pct.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Процент не может быть отрицательным");
        }
    }

    /**
     *
     * ПРОВЕРКА НА ПРОЦЕНТНОЕ РАСПРЕДЕЛЕНИЕ
     *
     */
    private void validateTotalPercentage(BigDecimal pct, Long excludeId) {
        BigDecimal sum = groupRepo.findAll().stream()
                .filter(g -> !g.getId().equals(excludeId))
                .map(BudgetGroup::getPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.add(pct).compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(
                    "Сумма процентов всех групп не может превышать 100%");
        }
    }
}
