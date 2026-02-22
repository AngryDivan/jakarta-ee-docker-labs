package edu.demo.rest;

import edu.demo.entity.Task;
import jakarta.enterprise.context.RequestScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/tasks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
public class TaskResource {

    @PersistenceContext(unitName = "labPU")
    private EntityManager em;

    @GET
    public List<Task> all() {
        return em.createQuery("select t from Task t order by t.id desc", Task.class)
                .getResultList();
    }

    @POST
    @Transactional
    public Task create(Task t) {
        t.setId(null);
        if (t.getTitle() == null || t.getTitle().isBlank()) {
            throw new BadRequestException("title is required");
        }
        em.persist(t);
        return t;
    }

    @PUT
    @Path("/{id}/toggle")
    @Transactional
    public Task toggle(@PathParam("id") long id) {
        Task t = em.find(Task.class, id);
        if (t == null) throw new NotFoundException();
        t.setDone(!t.isDone());
        return t;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public void delete(@PathParam("id") long id) {
        Task t = em.find(Task.class, id);
        if (t != null) em.remove(t);
    }
}