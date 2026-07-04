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
public class CommercialFeatureSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public CommercialFeatureSchemaInitializer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String product;
        try (Connection connection = dataSource.getConnection()) {
            product = connection.getMetaData().getDatabaseProductName().toLowerCase();
        }
        boolean h2 = product.contains("h2");
        createTables(h2);
        try (Connection connection = dataSource.getConnection()) {
            ensureUserNoticeColumns(connection, h2);
        }
        seedDefaultNotice();
    }

    private void createTables(boolean h2) {
        jdbcTemplate.execute(h2 ? """
                CREATE TABLE IF NOT EXISTS recharge_order (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  user_id BIGINT NOT NULL,
                  order_no VARCHAR(64) NOT NULL UNIQUE,
                  amount DECIMAL(10,2) NOT NULL,
                  points INT NOT NULL,
                  status VARCHAR(20) NOT NULL,
                  pay_method VARCHAR(30),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                  paid_at TIMESTAMP
                )
                """ : """
                CREATE TABLE IF NOT EXISTS recharge_order (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  order_no VARCHAR(64) NOT NULL UNIQUE,
                  amount DECIMAL(10,2) NOT NULL,
                  points INT NOT NULL,
                  status VARCHAR(20) NOT NULL DEFAULT 'pending',
                  pay_method VARCHAR(30) DEFAULT 'alipay_mock',
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  paid_at DATETIME NULL,
                  INDEX idx_recharge_user_created (user_id, created_at),
                  INDEX idx_recharge_status (status)
                ) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute(h2 ? """
                CREATE TABLE IF NOT EXISTS user_points_log (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  user_id BIGINT NOT NULL,
                  change_amount INT NOT NULL,
                  before_points INT NOT NULL,
                  after_points INT NOT NULL,
                  reason VARCHAR(50) NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                )
                """ : """
                CREATE TABLE IF NOT EXISTS user_points_log (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  user_id BIGINT NOT NULL,
                  change_amount INT NOT NULL,
                  before_points INT NOT NULL,
                  after_points INT NOT NULL,
                  reason VARCHAR(50) NOT NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_user_points_log_user_created (user_id, created_at),
                  INDEX idx_user_points_log_reason (reason)
                ) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute(h2 ? """
                CREATE TABLE IF NOT EXISTS system_notice (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  title VARCHAR(120) NOT NULL,
                  content CLOB NOT NULL,
                  status VARCHAR(20) NOT NULL,
                  is_popup BOOLEAN DEFAULT TRUE NOT NULL,
                  created_by BIGINT,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                )
                """ : """
                CREATE TABLE IF NOT EXISTS system_notice (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  title VARCHAR(120) NOT NULL,
                  content TEXT NOT NULL,
                  status VARCHAR(20) NOT NULL DEFAULT 'active',
                  is_popup TINYINT(1) NOT NULL DEFAULT 1,
                  created_by BIGINT NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_system_notice_status_popup (status, is_popup, created_at)
                ) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void ensureUserNoticeColumns(Connection connection, boolean h2) throws Exception {
        if (!tableExists(connection, "user_account")) {
            return;
        }
        ensureColumn(connection, "user_account", "last_notice_time",
                h2 ? "TIMESTAMP" : "DATETIME NULL COMMENT '\u6700\u540e\u516c\u544a\u9605\u8bfb\u65f6\u95f4'");
        ensureColumn(connection, "user_account", "notice_read_id",
                h2 ? "BIGINT" : "BIGINT NULL COMMENT '\u5df2\u8bfb\u516c\u544aID'");
    }

    private void seedDefaultNotice() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_notice WHERE status = ? AND is_popup = ?",
                Integer.class,
                "active",
                true
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO system_notice (title, content, status, is_popup, created_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                "\u7cfb\u7edf\u66f4\u65b0\u516c\u544a",
                "# DropAI \u7cfb\u7edf\u66f4\u65b0\u516c\u544a\n\n- \u79ef\u5206\u5145\u503c\u529f\u80fd\u5df2\u4e0a\u7ebf\n- \u751f\u6210\u4efb\u52a1\u5c06\u6309\u79ef\u5206\u6821\u9a8c\u6267\u884c\n\n---\n\n\u8bf7\u786e\u4fdd\u8d26\u6237\u79ef\u5206\u5145\u8db3\u540e\u518d\u4f7f\u7528\u751f\u6210\u529f\u80fd\u3002",
                "active",
                true
        );
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

    private void ensureColumn(Connection connection, String table, String column, String definition) throws Exception {
        if (columnExists(connection, table, column)) {
            return;
        }
        safeExecute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
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

    private void safeExecute(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (DataAccessException exception) {
            String message = (String.valueOf(exception.getMessage()) + " " +
                    String.valueOf(exception.getMostSpecificCause())).toLowerCase();
            if (message.contains("duplicate") || message.contains("already exists") || message.contains("duplicate key name")) {
                return;
            }
            throw exception;
        }
    }
}
