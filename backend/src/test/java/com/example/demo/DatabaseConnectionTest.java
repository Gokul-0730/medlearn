package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DatabaseConnectionTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testDatabaseConnection() throws Exception {
        assertNotNull(dataSource, "DataSource should not be null");
        try (Connection conn = dataSource.getConnection()) {
            assertNotNull(conn, "Connection should be valid");
            assertFalse(conn.isClosed(), "Connection should be open");
            System.out.println("[TEST] Successfully connected to Database: " + conn.getMetaData().getDatabaseProductName());
        }
    }

    @Test
    public void testMedicalKnowledgeTable() {
        assertNotNull(jdbcTemplate, "JdbcTemplate should not be null");
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM medical_knowledge", Integer.class);
        assertNotNull(count, "Count query should return a number");
        assertTrue(count > 0, "Medical knowledge table should have rows");
        System.out.println("[TEST] Medical knowledge rows count: " + count);
    }
}
