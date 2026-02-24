package org.example.util;

import org.example.model.entity.Category;
import org.example.model.enums.CategoryType;
import org.example.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private DataSeeder() {}

    public static void seed() {
        CategoryRepository repo = new CategoryRepository();
        if (!repo.findAll().isEmpty()) {
            log.debug("DataSeeder: категории уже есть, пропуск.");
            return;
        }

        List<String> incomeNames = List.of("Зарплата", "Фриланс", "Подарки", "Инвестиции", "Другое");
        List<String> expenseNames = List.of("Еда", "Транспорт", "Жильё", "Здоровье",
                "Развлечения", "Одежда", "Образование", "Другое");

        for (String name : incomeNames) {
            repo.save(Category.builder().name(name).type(CategoryType.INCOME).isDefault(true).build());
        }
        for (String name : expenseNames) {
            repo.save(Category.builder().name(name).type(CategoryType.EXPENSE).isDefault(true).build());
        }
        log.info("DataSeeder: добавлено {} категорий по умолчанию.",
                incomeNames.size() + expenseNames.size());
    }
}
