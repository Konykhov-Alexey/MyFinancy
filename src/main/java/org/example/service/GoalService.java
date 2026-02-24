package org.example.service;

import org.example.model.entity.SavingsGoal;
import org.example.model.enums.GoalStatus;
import org.example.repository.GoalRepository;
import org.example.repository.GoalRepository.GoalSort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class GoalService {

    private static final Logger log = LoggerFactory.getLogger(GoalService.class);

    private final GoalRepository repository;

    public GoalService(GoalRepository repository) {
        this.repository = repository;
    }

    public List<SavingsGoal> getAll(GoalSort sort) {
        return repository.findAll(sort);
    }

    public SavingsGoal create(String name, BigDecimal targetAmount,
                              LocalDate deadline, String description) {
        SavingsGoal goal = SavingsGoal.builder()
                .name(name.trim())
                .targetAmount(targetAmount)
                .currentAmount(BigDecimal.ZERO)
                .deadline(deadline)
                .description(description == null || description.isBlank() ? null : description.trim())
                .status(GoalStatus.ACTIVE)
                .build();
        SavingsGoal saved = repository.save(goal);
        log.info("Создана цель: {} (цель: {})", saved.getName(), saved.getTargetAmount());
        return saved;
    }

    /**
     * Пополняет цель на указанную сумму.
     * Если currentAmount достигает targetAmount — автоматически переводит в COMPLETED.
     */
    public SavingsGoal contribute(SavingsGoal goal, BigDecimal amount) {
        if (goal.getStatus() != GoalStatus.ACTIVE) {
            throw new IllegalStateException("Нельзя пополнить неактивную цель: " + goal.getName());
        }
        BigDecimal newAmount = goal.getCurrentAmount().add(amount);
        goal.setCurrentAmount(newAmount);
        if (newAmount.compareTo(goal.getTargetAmount()) >= 0) {
            goal.setStatus(GoalStatus.COMPLETED);
            log.info("Цель достигнута: {}", goal.getName());
        }
        return repository.save(goal);
    }

    /** Отменяет активную цель. */
    public SavingsGoal cancel(SavingsGoal goal) {
        if (goal.getStatus() == GoalStatus.COMPLETED) {
            throw new IllegalStateException("Нельзя отменить завершённую цель: " + goal.getName());
        }
        goal.setStatus(GoalStatus.CANCELLED);
        SavingsGoal saved = repository.save(goal);
        log.info("Цель отменена: {}", goal.getName());
        return saved;
    }

    public void delete(SavingsGoal goal) {
        repository.delete(goal);
    }
}
