package org.example.service;

import org.example.model.entity.Transaction;
import org.example.repository.TransactionFilter;
import org.example.repository.TransactionRepository;

import java.util.List;

public class TransactionService {

    private final TransactionRepository repository;

    public TransactionService(TransactionRepository repository) {
        this.repository = repository;
    }

    public List<Transaction> getPage(TransactionFilter filter, int page, int pageSize) {
        return repository.findAll(filter, page, pageSize);
    }

    public long count(TransactionFilter filter) {
        return repository.countAll(filter);
    }

    public Transaction save(Transaction transaction) {
        return repository.save(transaction);
    }

    public void delete(Transaction transaction) {
        repository.delete(transaction);
    }
}
