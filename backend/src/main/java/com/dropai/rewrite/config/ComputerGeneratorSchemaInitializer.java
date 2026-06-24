package com.dropai.rewrite.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class ComputerGeneratorSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public ComputerGeneratorSchemaInitializer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            boolean h2 = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("h2");
            createTables(h2);
            ensureColumns(connection, h2);
        }
    }

    private void createTables(boolean h2) {
        jdbcTemplate.execute(h2 ? """
                CREATE TABLE IF NOT EXISTS computer_generation_jobs (
                  id VARCHAR(64) PRIMARY KEY,
                  user_id BIGINT NOT NULL,
                  title VARCHAR(255) NOT NULL,
                  project_type VARCHAR(80),
                  tech_stack VARCHAR(120),
                  status VARCHAR(30) NOT NULL,
                  progress INT DEFAULT 0,
                  current_stage VARCHAR(80),
                  current_file VARCHAR(500),
                  input_text CLOB,
                  uploaded_files CLOB,
                  output_zip_path VARCHAR(500),
                  frontend_path VARCHAR(500),
                  backend_path VARCHAR(500),
                  sql_path VARCHAR(500),
                  paper_path VARCHAR(500),
                  preview_url VARCHAR(500),
                  error_message CLOB,
                  points_cost INT DEFAULT 0,
                  points_charged BOOLEAN DEFAULT FALSE NOT NULL,
                  created_at TIMESTAMP NOT NULL,
                  updated_at TIMESTAMP NOT NULL
                )
                """ : """
                CREATE TABLE IF NOT EXISTS computer_generation_jobs (
                  id VARCHAR(64) PRIMARY KEY COMMENT '生成任务ID',
                  user_id BIGINT NOT NULL COMMENT '所属用户',
                  title VARCHAR(255) NOT NULL COMMENT '项目题目',
                  project_type VARCHAR(80) COMMENT '项目类型',
                  tech_stack VARCHAR(120) COMMENT '技术栈',
                  status VARCHAR(30) NOT NULL COMMENT '任务状态',
                  progress INT DEFAULT 0 COMMENT '进度百分比',
                  current_stage VARCHAR(80) COMMENT '当前阶段',
                  current_file VARCHAR(500) COMMENT '当前生成文件',
                  input_text LONGTEXT COMMENT '输入需求和解析文本',
                  uploaded_files LONGTEXT COMMENT '上传文件名',
                  output_zip_path VARCHAR(500) COMMENT 'ZIP路径',
                  frontend_path VARCHAR(500) COMMENT '前端路径',
                  backend_path VARCHAR(500) COMMENT '后端路径',
                  sql_path VARCHAR(500) COMMENT 'SQL路径',
                  paper_path VARCHAR(500) COMMENT '论文路径',
                  preview_url VARCHAR(500) COMMENT '预览URL',
                  error_message TEXT COMMENT '错误信息',
                  points_cost INT DEFAULT 0 COMMENT '预计积分',
                  points_charged TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已扣积分',
                  created_at DATETIME NOT NULL COMMENT '创建时间',
                  updated_at DATETIME NOT NULL COMMENT '更新时间',
                  INDEX idx_computer_job_user_created (user_id, created_at),
                  INDEX idx_computer_job_status (status)
                ) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute(h2 ? """
                CREATE TABLE IF NOT EXISTS computer_generated_files (
                  id VARCHAR(64) PRIMARY KEY,
                  job_id VARCHAR(64) NOT NULL,
                  file_type VARCHAR(50),
                  file_name VARCHAR(500),
                  file_path VARCHAR(500),
                  file_size BIGINT DEFAULT 0,
                  created_at TIMESTAMP NOT NULL
                )
                """ : """
                CREATE TABLE IF NOT EXISTS computer_generated_files (
                  id VARCHAR(64) PRIMARY KEY COMMENT '文件ID',
                  job_id VARCHAR(64) NOT NULL COMMENT '任务ID',
                  file_type VARCHAR(50) COMMENT '文件类型',
                  file_name VARCHAR(500) COMMENT '展示文件名',
                  file_path VARCHAR(500) COMMENT '服务器存储路径',
                  file_size BIGINT DEFAULT 0 COMMENT '文件大小',
                  created_at DATETIME NOT NULL COMMENT '创建时间',
                  INDEX idx_computer_file_job (job_id)
                ) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute(h2 ? """
                CREATE TABLE IF NOT EXISTS computer_preview_instances (
                  id VARCHAR(64) PRIMARY KEY,
                  job_id VARCHAR(64) NOT NULL,
                  preview_id VARCHAR(64) NOT NULL,
                  preview_url VARCHAR(500),
                  preview_path VARCHAR(500),
                  status VARCHAR(30),
                  created_at TIMESTAMP NOT NULL,
                  expired_at TIMESTAMP
                )
                """ : """
                CREATE TABLE IF NOT EXISTS computer_preview_instances (
                  id VARCHAR(64) PRIMARY KEY COMMENT '预览实例ID',
                  job_id VARCHAR(64) NOT NULL COMMENT '任务ID',
                  preview_id VARCHAR(64) NOT NULL COMMENT '随机预览ID',
                  preview_url VARCHAR(500) COMMENT '预览地址',
                  preview_path VARCHAR(500) COMMENT '预览目录',
                  status VARCHAR(30) COMMENT '状态',
                  created_at DATETIME NOT NULL COMMENT '创建时间',
                  expired_at DATETIME COMMENT '过期时间',
                  INDEX idx_computer_preview_job (job_id),
                  UNIQUE KEY uk_computer_preview_id (preview_id)
                ) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void ensureColumns(Connection connection, boolean h2) throws SQLException {
        if (!columnExists(connection, "computer_generation_jobs", "current_file")) {
            safeExecute("ALTER TABLE computer_generation_jobs ADD COLUMN current_file " +
                    (h2 ? "VARCHAR(500)" : "VARCHAR(500) COMMENT '当前生成文件'"));
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, tableName, columnName)) {
            if (columns.next()) return true;
        }
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, tableName.toUpperCase(), columnName.toUpperCase())) {
            return columns.next();
        }
    }

    private void safeExecute(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (DataAccessException exception) {
            String message = String.valueOf(exception.getMessage()).toLowerCase();
            if (message.contains("duplicate") || message.contains("already exists") || message.contains("重复")) return;
            throw exception;
        }
    }
}
