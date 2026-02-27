package org.example.repository;

import org.example.config.HibernateUtil;
import org.example.model.entity.Debt;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DebtRepository {

    private static final Logger log = LoggerFactory.getLogger(DebtRepository.class);

    public enum DebtSort { BY_REMAINING, BY_NEXT_PAYMENT }

    public List<Debt> findAll(DebtSort sort) {
        String hql = switch (sort) {
            case BY_REMAINING -> """
                    FROM Debt
                    ORDER BY (totalAmount - paidAmount) DESC, name
                    """;
            case BY_NEXT_PAYMENT -> """
                    FROM Debt
                    ORDER BY CASE WHEN nextPaymentDate IS NULL THEN 1 ELSE 0 END,
                             nextPaymentDate,
                             name
                    """;
        };
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(hql, Debt.class).list();
        }
    }

    public Debt save(Debt debt) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            Debt merged = session.merge(debt);
            tx.commit();
            log.debug("Сохранён долг: {}", merged.getName());
            return merged;
        }
    }

    public void delete(Debt debt) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            Debt managed = session.get(Debt.class, debt.getId());
            if (managed != null) {
                session.remove(managed);
                log.debug("Удалён долг: {}", managed.getName());
            }
            tx.commit();
        }
    }
}
