package org.example.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.model.enums.CategoryType;

import java.math.BigDecimal;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryType type;

    @Column(nullable = false)
    private boolean isDefault;

    @Column(precision = 19, scale = 2)
    private BigDecimal monthlyLimit;
}
