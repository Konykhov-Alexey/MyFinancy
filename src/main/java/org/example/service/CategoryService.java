package org.example.service;

import org.example.config.HibernateUtil;
import org.example.model.entity.Category;
import org.example.model.enums.CategoryType;
import org.example.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository repository;

    public CategoryService(CategoryRepository repository) {
        this.repository = repository;
    }

    public List<Category> getAll() {
        return repository.findAll();
    }

    public List<Category> getByType(CategoryType type) {
        return repository.findByType(type);
    }

    public Category add(String name, CategoryType type) {
        Category category = Category.builder()
                .name(name.trim())
                .type(type)
                .isDefault(false)
                .build();
        return repository.save(category);
    }

    public void delete(Category category) {
        if (category.isDefault()) {
            throw new IllegalStateException("Нельзя удалить стандартную категорию: " + category.getName());
        }
        repository.delete(category);
    }

    public Category updateLimit(Category category, BigDecimal limit) {
        category.setMonthlyLimit(limit);
        return repository.save(category);
    }

    /** Сумма расходов по категории за текущий месяц. */
    public BigDecimal getSpentThisMonth(Category category) {
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = start.plusMonths(1);
        try (var session = HibernateUtil.getSessionFactory().openSession()) {
            BigDecimal result = session.createQuery(
                    "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                    "WHERE t.category = :cat AND t.date >= :start AND t.date < :end",
                    BigDecimal.class)
                    .setParameter("cat", category)
                    .setParameter("start", start)
                    .setParameter("end", end)
                    .uniqueResult();
            return result != null ? result : BigDecimal.ZERO;
        }
    }
}
