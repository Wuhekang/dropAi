package com.dropai.rewrite.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;

@Component
public class PointSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public PointSchemaInitializer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String product;
        try (Connection connection = dataSource.getConnection()) {
            product = connection.getMetaData().getDatabaseProductName().toLowerCase();
            ensureUserPointColumns(connection);
        }
        createPointTables(product);
        seedPricing();
    }

    private void ensureUserPointColumns(Connection connection) throws Exception {
        if (!tableExists(connection, "user_account")) {
            return;
        }
        if (ensureColumn(connection, "points", "INT NOT NULL DEFAULT 1000")) {
            jdbcTemplate.update("UPDATE user_account SET points = 1000 WHERE points IS NULL");
        }
        if (ensureColumn(connection, "total_points", "INT NOT NULL DEFAULT 1000")) {
            jdbcTemplate.update("UPDATE user_account SET total_points = 1000 WHERE total_points IS NULL");
        }
        if (ensureColumn(connection, "used_points", "INT NOT NULL DEFAULT 0")) {
            jdbcTemplate.update("UPDATE user_account SET used_points = 0 WHERE used_points IS NULL");
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        for (String candidate : List.of(table, table.toUpperCase())) {
            try (ResultSet rs = metaData.getTables(null, null, candidate, null)) {
                if (rs.next()) return true;
            }
        }
        return false;
    }

    private boolean ensureColumn(Connection connection, String column, String definition) throws Exception {
        if (columnExists(connection, "user_account", column)) {
            return true;
        }
        safeExecute("ALTER TABLE user_account ADD COLUMN " + column + " " + definition);
        try (Connection freshConnection = dataSource.getConnection()) {
            return columnExists(freshConnection, "user_account", column);
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        for (String candidate : List.of(table, table.toUpperCase())) {
            try (ResultSet rs = metaData.getColumns(null, null, candidate, column)) {
                if (rs.next()) return true;
            }
            try (ResultSet rs = metaData.getColumns(null, null, candidate, column.toUpperCase())) {
                if (rs.next()) return true;
            }
        }
        return false;
    }

    private void createPointTables(String product) {
        boolean h2 = product.contains("h2");
        jdbcTemplate.execute(h2 ? """
                CREATE TABLE IF NOT EXISTS point_transactions (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  user_id BIGINT NOT NULL,
                  feature_code VARCHAR(50) NOT NULL,
                  feature_name VARCHAR(100) NOT NULL,
                  points_change INT NOT NULL,
                  balance_after INT NOT NULL,
                  remark VARCHAR(255),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                )
                """ : """
                CREATE TABLE IF NOT EXISTS point_transactions (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  feature_code VARCHAR(50) NOT NULL,
                  feature_name VARCHAR(100) NOT NULL,
                  points_change INT NOT NULL,
                  balance_after INT NOT NULL,
                  remark VARCHAR(255),
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_point_tx_user_created (user_id, created_at),
                  INDEX idx_point_tx_feature (feature_code)
                ) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute(h2 ? """
                CREATE TABLE IF NOT EXISTS feature_pricing (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  feature_code VARCHAR(50) NOT NULL UNIQUE,
                  feature_name VARCHAR(100) NOT NULL,
                  cost_points INT NOT NULL,
                  enabled BOOLEAN DEFAULT TRUE NOT NULL
                )
                """ : """
                CREATE TABLE IF NOT EXISTS feature_pricing (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  feature_code VARCHAR(50) NOT NULL UNIQUE,
                  feature_name VARCHAR(100) NOT NULL,
                  cost_points INT NOT NULL,
                  enabled TINYINT(1) NOT NULL DEFAULT 1
                ) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void seedPricing() {
        seed("DESIGN_GENERATE", "毕业设计成果包生成", 100);
        seed("CAD_GENERATE", "CAD图纸生成", 50);
        seed("MODEL_GENERATE", "三维模型生成", 50);
        seed("DOCX_GENERATE", "文档生成", 30);
        seed("ZIP_EXPORT", "成果包导出", 20);
    }

    private void seed(String code, String name, int cost) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_pricing WHERE feature_code = ?",
                Integer.class,
                code
        );
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO feature_pricing (feature_code, feature_name, cost_points, enabled) VALUES (?, ?, ?, ?)",
                    code,
                    name,
                    cost,
                    true
            );
        }
    }

    private void safeExecute(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (DataAccessException exception) {
            String message = (String.valueOf(exception.getMessage()) + " " +
                    String.valueOf(exception.getMostSpecificCause())).toLowerCase();
            if (message.contains("duplicate") ||
                    message.contains("already exists") ||
                    message.contains("重复")) {
                return;
            }
            throw exception;
        }
    }
}
