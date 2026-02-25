package org.example.repository;

import org.example.config.HibernateUtil;
import org.example.model.entity.Category;
import org.example.model.enums.CategoryType;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CategoryRepository {

    private static final Logger log = LoggerFactory.getLogger(CategoryRepository.class);

    public List<Category> findAll() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM Category ORDER BY name", Category.class).list();
        }
    }

    public List<Category> findByType(CategoryType type) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "FROM Category c LEFT JOIN FETCH c.group WHERE c.type = :type ORDER BY c.isDefault DESC, c.name", Category.class)
                    .setParameter("type", type)
                    .list();
        }
    }

    public Category save(Category category) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            Category merged = session.merge(category);
            tx.commit();
            log.debug("Сохранена категория: {}", merged.getName());
            return merged;
        }
    }

    public void delete(Category category) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            Category managed = session.get(Category.class, category.getId());
            if (managed != null) {
                session.remove(managed);
                log.debug("Удалена категория: {}", managed.getName());
            }
            tx.commit();
        }
    }
}
