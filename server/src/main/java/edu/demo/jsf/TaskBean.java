package edu.demo.jsf;

import edu.demo.entity.Task;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;

@Named
@RequestScoped
public class TaskBean {

    @PersistenceContext(unitName = "labPU")
    private EntityManager em;

    private String title;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public List<Task> getTasks() {
        return em.createQuery("select t from Task t order by t.id desc", Task.class)
                .getResultList();
    }

    @Transactional
    public void add() {
        if (title == null || title.isBlank()) return;
        Task t = new Task();
        t.setTitle(title.trim());
        t.setDone(false);
        em.persist(t);
        title = "";
    }

    @Transactional
    public void toggle(long id) {
        Task t = em.find(Task.class, id);
        if (t != null) t.setDone(!t.isDone());
    }

    @Transactional
    public void delete(long id) {
        Task t = em.find(Task.class, id);
        if (t != null) em.remove(t);
    }
}