package org.example.repository;

import org.example.config.HibernateUtil;
import org.example.model.entity.BudgetGroup;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class BudgetGroupRepository {

    private static final Logger log = LoggerFactory.getLogger(BudgetGroupRepository.class);

    public List<BudgetGroup> findAll() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "FROM BudgetGroup ORDER BY sortOrder, id", BudgetGroup.class).list();
        }
    }

    public BudgetGroup save(BudgetGroup group) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            BudgetGroup merged = session.merge(group);
            tx.commit();
            log.debug("Сохранена бюджетная группа: {}", merged.getName());
            return merged;
        }
    }

    public void delete(BudgetGroup group) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            // Отвязать все категории от этой группы перед удалением
            session.createMutationQuery(
                    "UPDATE Category SET group = null WHERE group = :g")
                    .setParameter("g", session.getReference(BudgetGroup.class, group.getId()))
                    .executeUpdate();
            BudgetGroup managed = session.get(BudgetGroup.class, group.getId());
            if (managed != null) {
                session.remove(managed);
                log.debug("Удалена бюджетная группа: {}", managed.getName());
            }
            tx.commit();
        }
    }
}
