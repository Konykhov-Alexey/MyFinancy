package org.example.repository;

import org.example.config.HibernateUtil;
import org.example.model.entity.Transaction;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TransactionRepository {

    private static final Logger log = LoggerFactory.getLogger(TransactionRepository.class);

    public List<Transaction> findAll(TransactionFilter filter, int page, int pageSize) {
        Map<String, Object> params = new LinkedHashMap<>();
        String where = buildWhere(filter, params);
        String hql = "SELECT t FROM Transaction t JOIN FETCH t.category" + where
                + " ORDER BY t.date DESC, t.id DESC";

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            var query = session.createQuery(hql, Transaction.class)
                    .setFirstResult(page * pageSize)
                    .setMaxResults(pageSize);
            params.forEach(query::setParameter);
            return query.list();
        }
    }

    public long countAll(TransactionFilter filter) {
        Map<String, Object> params = new LinkedHashMap<>();
        String where = buildWhere(filter, params);
        String hql = "SELECT COUNT(t) FROM Transaction t" + where;

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            var query = session.createQuery(hql, Long.class);
            params.forEach(query::setParameter);
            Long result = query.uniqueResult();
            return result != null ? result : 0L;
        }
    }

    public Transaction save(Transaction transaction) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            Transaction merged = session.merge(transaction);
            tx.commit();
            log.debug("Сохранена транзакция id={}", merged.getId());
            return merged;
        }
    }

    public void delete(Transaction transaction) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            Transaction managed = session.get(Transaction.class, transaction.getId());
            if (managed != null) {
                session.remove(managed);
                log.debug("Удалена транзакция id={}", transaction.getId());
            }
            tx.commit();
        }
    }

    private String buildWhere(TransactionFilter f, Map<String, Object> params) {
        List<String> conditions = new ArrayList<>();

        if (f.type() != null) {
            conditions.add("t.type = :type");
            params.put("type", f.type());
        }
        if (f.categoryId() != null) {
            conditions.add("t.category.id = :catId");
            params.put("catId", f.categoryId());
        }
        if (f.dateFrom() != null) {
            conditions.add("t.date >= :dateFrom");
            params.put("dateFrom", f.dateFrom());
        }
        if (f.dateTo() != null) {
            conditions.add("t.date <= :dateTo");
            params.put("dateTo", f.dateTo());
        }
        if (f.search() != null && !f.search().isBlank()) {
            conditions.add("LOWER(t.comment) LIKE :search");
            params.put("search", "%" + f.search().toLowerCase() + "%");
        }

        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }
}
