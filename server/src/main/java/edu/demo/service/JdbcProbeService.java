package edu.demo.service;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@ApplicationScoped
public class JdbcProbeService {

    @Resource(lookup = "jdbc/LabDS")
    private DataSource ds;

    public String dbNow() {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("select now()");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getString(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}