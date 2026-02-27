package org.example.repository;

import org.example.config.HibernateUtil;
import org.example.model.entity.Debt;
import org.example.model.enums.DebtStatus;
import org.example.repository.DebtRepository.DebtSort;
import org.hibernate.Session;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DebtRepositoryTest {

    private static DebtRepository repo;

    @BeforeAll
    static void setup() {
        repo = new DebtRepository();
    }

    @BeforeEach
    void clean() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = s.beginTransaction();
            s.createMutationQuery("DELETE FROM Debt").executeUpdate();
            tx.commit();
        }
    }

    private Debt makeDebt(String name, BigDecimal total, BigDecimal paid) {
        return Debt.builder()
                .name(name)
                .totalAmount(total)
                .paidAmount(paid)
                .status(DebtStatus.ACTIVE)
                .build();
    }

    @Test
    void save_and_findAll_returnsDebt() {
        repo.save(makeDebt("Кредит", new BigDecimal("50000"), BigDecimal.ZERO));
        List<Debt> all = repo.findAll(DebtSort.BY_REMAINING);
        assertEquals(1, all.size());
        assertEquals("Кредит", all.get(0).getName());
    }

    @Test
    void findAll_byRemaining_largerRemainingFirst() {
        repo.save(makeDebt("Маленький", new BigDecimal("10000"), BigDecimal.ZERO));
        repo.save(makeDebt("Большой",   new BigDecimal("100000"), BigDecimal.ZERO));
        List<Debt> all = repo.findAll(DebtSort.BY_REMAINING);
        assertEquals("Большой", all.get(0).getName());
    }

    @Test
    void findAll_byNextPayment_nullsLast() {
        Debt withDate = makeDebt("С датой", new BigDecimal("5000"), BigDecimal.ZERO);
        withDate.setNextPaymentDate(LocalDate.now().plusDays(5));
        repo.save(withDate);
        repo.save(makeDebt("Без даты", new BigDecimal("5000"), BigDecimal.ZERO));
        List<Debt> all = repo.findAll(DebtSort.BY_NEXT_PAYMENT);
        assertEquals("С датой", all.get(0).getName());
    }

    @Test
    void delete_removesDebt() {
        Debt saved = repo.save(makeDebt("Удаляемый", new BigDecimal("1000"), BigDecimal.ZERO));
        repo.delete(saved);
        assertTrue(repo.findAll(DebtSort.BY_REMAINING).isEmpty());
    }
}
