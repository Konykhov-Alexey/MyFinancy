package org.example.service;

import org.example.model.entity.Debt;
import org.example.model.enums.DebtStatus;
import org.example.repository.DebtRepository;
import org.example.repository.DebtRepository.DebtSort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class DebtService {

    private static final Logger log = LoggerFactory.getLogger(DebtService.class);

    private final DebtRepository repository;

    public DebtService(DebtRepository repository) {
        this.repository = repository;
    }

    public List<Debt> getAll(DebtSort sort) {
        return repository.findAll(sort);
    }

    public Debt create(String name, String creditorName, BigDecimal totalAmount,
                       BigDecimal interestRate, BigDecimal minimumPayment,
                       LocalDate nextPaymentDate, LocalDate deadline) {
        Debt debt = Debt.builder()
                .name(name.trim())
                .creditorName(creditorName == null || creditorName.isBlank() ? null : creditorName.trim())
                .totalAmount(totalAmount)
                .paidAmount(BigDecimal.ZERO)
                .interestRate(interestRate)
                .minimumPayment(minimumPayment)
                .nextPaymentDate(nextPaymentDate)
                .deadline(deadline)
                .status(DebtStatus.ACTIVE)
                .build();
        Debt saved = repository.save(debt);
        log.info("Создан долг: {} (сумма: {})", saved.getName(), saved.getTotalAmount());
        return saved;
    }

    /**
     * Регистрирует выплату по долгу.
     * Если paidAmount >= totalAmount — автоматически переводит в PAID.
     */
    public Debt repay(Debt debt, BigDecimal amount) {
        if (debt.getStatus() != DebtStatus.ACTIVE) {
            throw new IllegalStateException("Нельзя вносить платёж по неактивному долгу: " + debt.getName());
        }
        debt.setPaidAmount(debt.getPaidAmount().add(amount));
        if (debt.getPaidAmount().compareTo(debt.getTotalAmount()) >= 0) {
            debt.setStatus(DebtStatus.PAID);
            log.info("Долг полностью погашен: {}", debt.getName());
        }
        return repository.save(debt);
    }

    /** Отменяет активный долг. */
    public Debt cancel(Debt debt) {
        if (debt.getStatus() == DebtStatus.PAID) {
            throw new IllegalStateException("Нельзя отменить погашённый долг: " + debt.getName());
        }
        debt.setStatus(DebtStatus.CANCELLED);
        Debt saved = repository.save(debt);
        log.info("Долг отменён: {}", debt.getName());
        return saved;
    }

    public void delete(Debt debt) {
        repository.delete(debt);
    }
}
