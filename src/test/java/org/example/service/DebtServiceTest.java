package org.example.service;

import org.example.config.HibernateUtil;
import org.example.model.entity.Debt;
import org.example.model.enums.DebtStatus;
import org.example.repository.DebtRepository;
import org.example.repository.DebtRepository.DebtSort;
import org.hibernate.Session;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DebtServiceTest {

    private static DebtService service;

    @BeforeAll
    static void setup() {
        service = new DebtService(new DebtRepository());
    }

    @BeforeEach
    void clean() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = s.beginTransaction();
            s.createMutationQuery("DELETE FROM Debt").executeUpdate();
            tx.commit();
        }
    }

    private Debt createSimple(String name, String total) {
        return service.create(name, null, new BigDecimal(total),
                null, null, null, null);
    }

    @Test
    void create_setsActiveStatusAndZeroPaid() {
        Debt d = createSimple("Кредит", "100000");
        assertEquals(DebtStatus.ACTIVE, d.getStatus());
        assertEquals(BigDecimal.ZERO, d.getPaidAmount());
        assertNotNull(d.getId());
    }

    @Test
    void create_storesCreditorAndRate() {
        Debt d = service.create("Ипотека", "Сбербанк",
                new BigDecimal("3000000"), new BigDecimal("14.5"),
                new BigDecimal("30000"), LocalDate.now().plusDays(15),
                LocalDate.now().plusYears(20));
        assertEquals("Сбербанк", d.getCreditorName());
        assertEquals(0, new BigDecimal("14.5").compareTo(d.getInterestRate()));
    }

    @Test
    void repay_increasesPaidAmount() {
        Debt d = createSimple("Кредит", "100000");
        Debt updated = service.repay(d, new BigDecimal("30000"));
        assertEquals(0, new BigDecimal("30000").compareTo(updated.getPaidAmount()));
        assertEquals(DebtStatus.ACTIVE, updated.getStatus());
    }

    @Test
    void repay_transitsToPaidWhenFullyRepaid() {
        Debt d = createSimple("Маленький", "10000");
        Debt updated = service.repay(d, new BigDecimal("10000"));
        assertEquals(DebtStatus.PAID, updated.getStatus());
    }

    @Test
    void repay_transitsToPaidOnOverpayment() {
        Debt d = createSimple("Маленький", "10000");
        Debt updated = service.repay(d, new BigDecimal("15000"));
        assertEquals(DebtStatus.PAID, updated.getStatus());
    }

    @Test
    void repay_throwsOnCancelledDebt() {
        Debt d = createSimple("Отменённый", "50000");
        service.cancel(d);
        assertThrows(IllegalStateException.class,
                () -> service.repay(d, new BigDecimal("1000")));
    }

    @Test
    void cancel_setsStatusCancelled() {
        Debt d = createSimple("Долг", "20000");
        Debt cancelled = service.cancel(d);
        assertEquals(DebtStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void cancel_throwsOnPaidDebt() {
        Debt d = createSimple("Мини", "1000");
        service.repay(d, new BigDecimal("1000"));
        assertThrows(IllegalStateException.class, () -> service.cancel(d));
    }

    @Test
    void delete_removesFromDb() {
        Debt d = createSimple("Удаляемый", "5000");
        service.delete(d);
        List<Debt> all = service.getAll(DebtSort.BY_REMAINING);
        assertTrue(all.isEmpty());
    }
}
