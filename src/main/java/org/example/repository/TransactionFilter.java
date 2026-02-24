package org.example.repository;

import org.example.model.enums.TransactionType;

import java.time.LocalDate;

public record TransactionFilter(
        TransactionType type,
        Long categoryId,
        LocalDate dateFrom,
        LocalDate dateTo,
        String search
) {
    public static TransactionFilter empty() {
        return new TransactionFilter(null, null, null, null, null);
    }
}
