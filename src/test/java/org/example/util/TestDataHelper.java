package org.example.util;

import org.example.config.HibernateUtil;
import org.example.model.entity.Category;
import org.example.model.entity.Transaction;
import org.example.model.enums.CategoryType;
import org.example.model.enums.TransactionType;
import org.hibernate.Session;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TestDataHelper {

    public static void clearAll() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = s.beginTransaction();
            s.createMutationQuery("DELETE FROM Transaction").executeUpdate();
            s.createMutationQuery("DELETE FROM Category").executeUpdate();
            tx.commit();
        }
    }

    public static Category saveCategory(String name) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = s.beginTransaction();
            Category c = new Category();
            c.setName(name);
            c.setType(CategoryType.EXPENSE);
            c.setIcon("F111");
            s.persist(c);
            tx.commit();
            return c;
        }
    }

    public static void saveExpense(Category cat, LocalDate date, BigDecimal amount) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = s.beginTransaction();
            Transaction t = new Transaction();
            t.setCategory(cat);
            t.setDate(date);
            t.setAmount(amount);
            t.setType(TransactionType.EXPENSE);
            t.setComment("");
            s.persist(t);
            tx.commit();
        }
    }
}
