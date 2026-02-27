package org.example.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.model.enums.DebtStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "debts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Debt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column
    private String creditorName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount;

    /** % годовых, nullable — не все долги имеют известную ставку */
    @Column(precision = 6, scale = 3)
    private BigDecimal interestRate;

    /** Минимальный ежемесячный платёж, nullable */
    @Column(precision = 19, scale = 2)
    private BigDecimal minimumPayment;

    /** Дата следующего обязательного платежа */
    @Column
    private LocalDate nextPaymentDate;

    /** Целевая дата полного погашения */
    @Column
    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DebtStatus status;
}
