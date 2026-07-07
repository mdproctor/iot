package io.casehub.iot.webapp.app;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.DriverManager;
import java.util.Map;

public class WebappPostgresTestResource implements QuarkusTestResourceLifecycleManager {

    private PostgreSQLContainer<?> container;

    @Override
    public Map<String, String> start() {
        container = new PostgreSQLContainer<>("postgres:16-alpine");
        container.start();

        createDatabase("iot_work");
        createDatabase("iot_ras");

        String jdbcUrl = container.getJdbcUrl();
        int dbNameEnd = jdbcUrl.indexOf('?');
        String baseWithoutDb = jdbcUrl.substring(0, jdbcUrl.lastIndexOf('/', dbNameEnd > 0 ? dbNameEnd : jdbcUrl.length()));
        String queryString = dbNameEnd > 0 ? jdbcUrl.substring(dbNameEnd) : "";

        return Map.ofEntries(
                Map.entry("quarkus.datasource.db-kind", "postgresql"),
                Map.entry("quarkus.datasource.jdbc.url", jdbcUrl),
                Map.entry("quarkus.datasource.username", container.getUsername()),
                Map.entry("quarkus.datasource.password", container.getPassword()),

                Map.entry("quarkus.datasource.iot-work.db-kind", "postgresql"),
                Map.entry("quarkus.datasource.iot-work.jdbc.url", baseWithoutDb + "/iot_work" + queryString),
                Map.entry("quarkus.datasource.iot-work.username", container.getUsername()),
                Map.entry("quarkus.datasource.iot-work.password", container.getPassword()),

                Map.entry("quarkus.datasource.iot-ras.db-kind", "postgresql"),
                Map.entry("quarkus.datasource.iot-ras.jdbc.url", baseWithoutDb + "/iot_ras" + queryString),
                Map.entry("quarkus.datasource.iot-ras.username", container.getUsername()),
                Map.entry("quarkus.datasource.iot-ras.password", container.getPassword())
        );
    }

    private void createDatabase(String dbName) {
        try (var conn = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE " + dbName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create database: " + dbName, e);
        }
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
