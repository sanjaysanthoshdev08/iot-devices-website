package com.hykon.iot_backend.integration;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;

public final class GateTestDatabaseInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String DATABASE = "hykon_iot_gate_test";
    private static final String JDBC_URL =
            "jdbc:postgresql://localhost:5432/" + DATABASE;

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        String password = System.getenv("HYKON_GATE_TEST_DB_PASSWORD");

        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "HYKON_GATE_TEST_DB_PASSWORD is required for gate integration tests"
            );
        }

        // Verify the dedicated database before application initialization.
        try (var connection = DriverManager.getConnection(
                JDBC_URL, "postgres", password
        ); var statement = connection.createStatement()) {

            try (var result = statement.executeQuery(
                    "SELECT current_database()"
            )) {
                if (!result.next()
                        || !DATABASE.equals(result.getString(1))) {
                    throw new IllegalStateException(
                            "Gate tests must use hykon_iot_gate_test"
                    );
                }
            }

            try (var result = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_type = 'BASE TABLE'
                    """)) {
                result.next();

                if (result.getLong(1) != 0) {
                    throw new IllegalStateException(
                            "Gate test database must be empty before this test run"
                    );
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not validate the gate test database connection",
                    exception
            );
        }

        Map<String, Object> settings = Map.ofEntries(
                Map.entry("spring.datasource.url", JDBC_URL),
                Map.entry("spring.datasource.username", "postgres"),
                Map.entry("spring.datasource.password", password),
                Map.entry("spring.datasource.hikari.jdbc-url", JDBC_URL),
                Map.entry("spring.datasource.hikari.username", "postgres"),
                Map.entry("spring.datasource.hikari.password", password),
                Map.entry("spring.datasource.hikari.maximum-pool-size", "12"),
                Map.entry("spring.jpa.hibernate.ddl-auto", "create-drop"),
                Map.entry("spring.jpa.open-in-view", "false"),
                Map.entry("spring.sql.init.mode", "never"),
                Map.entry("access.verification.max-attempts", "5"),
                Map.entry("access.verification.lockout-seconds", "60"),
                Map.entry("jwt.access-token-expiration", "300000"),
                Map.entry("jwt.refresh-token-expiration", "3600000"),
                Map.entry(
                        "jwt.secret",
                        Base64.getEncoder().encodeToString(new byte[64])
                )
        );

        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("guardedGateTestSettings", settings)
        );
    }
}