package org.example.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "budget_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Доля от месячного дохода (0–100), например 50.00 = 50% */
    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal percentage;

    /** Hex-цвет для UI-индикатора, например "#FF2D55" */
    @Column(length = 16)
    private String color;

    /** Порядок ручной сортировки */
    @Column(name = "sort_order")
    private int sortOrder;
}
