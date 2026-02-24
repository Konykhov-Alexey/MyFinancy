package org.example.repository;

import org.example.config.HibernateUtil;
import org.example.model.entity.SavingsGoal;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class GoalRepository {

    private static final Logger log = LoggerFactory.getLogger(GoalRepository.class);

    public enum GoalSort { BY_PROGRESS, BY_DEADLINE }

    public List<SavingsGoal> findAll(GoalSort sort) {
        String hql = switch (sort) {
            case BY_PROGRESS -> """
                    FROM SavingsGoal
                    ORDER BY CASE WHEN targetAmount > 0
                                  THEN currentAmount / targetAmount
                                  ELSE 0 END DESC,
                             name
                    """;
            case BY_DEADLINE -> """
                    FROM SavingsGoal
                    ORDER BY CASE WHEN deadline IS NULL THEN 1 ELSE 0 END,
                             deadline,
                             name
                    """;
        };
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(hql, SavingsGoal.class).list();
        }
    }

    public SavingsGoal save(SavingsGoal goal) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            SavingsGoal merged = session.merge(goal);
            tx.commit();
            log.debug("Сохранена цель: {}", merged.getName());
            return merged;
        }
    }

    public void delete(SavingsGoal goal) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            SavingsGoal managed = session.get(SavingsGoal.class, goal.getId());
            if (managed != null) {
                session.remove(managed);
                log.debug("Удалена цель: {}", managed.getName());
            }
            tx.commit();
        }
    }
}
