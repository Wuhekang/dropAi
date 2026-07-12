package com.dropai.rewrite.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

@Component
public class WritingGenerationSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public WritingGenerationSchemaInitializer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        boolean h2;
        try (Connection connection = dataSource.getConnection()) {
            h2 = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("h2");
        }
        for (String sql : h2 ? h2Statements() : mysqlStatements()) {
            safeExecute(sql);
        }
    }

    private List<String> mysqlStatements() {
        return List.of(
                "CREATE TABLE IF NOT EXISTS writing_project (id VARCHAR(64) PRIMARY KEY,user_id BIGINT NOT NULL,title VARCHAR(255) NOT NULL,major VARCHAR(255),document_type VARCHAR(80),target_word_count INT NOT NULL DEFAULT 8000,abstract_word_count INT NOT NULL DEFAULT 300,keyword_count INT NOT NULL DEFAULT 4,chapter_count INT NOT NULL DEFAULT 0,reference_count INT NOT NULL DEFAULT 20,chinese_reference_count INT NOT NULL DEFAULT 8,english_reference_count INT NOT NULL DEFAULT 12,year_start INT,year_end INT,citation_style VARCHAR(40) NOT NULL DEFAULT 'GB/T 7714',writing_tone VARCHAR(80),generate_english_abstract TINYINT(1) NOT NULL DEFAULT 1,generate_toc TINYINT(1) NOT NULL DEFAULT 1,generate_figure_list TINYINT(1) NOT NULL DEFAULT 1,generate_table_list TINYINT(1) NOT NULL DEFAULT 1,skip_references TINYINT(1) NOT NULL DEFAULT 0,requirements TEXT,abstract_text LONGTEXT,english_abstract LONGTEXT,keywords_json TEXT,english_keywords_json TEXT,preview_text LONGTEXT,status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',current_stage VARCHAR(120),progress INT NOT NULL DEFAULT 0,error_message TEXT,search_provider VARCHAR(80),search_status VARCHAR(40),search_message TEXT,quality_report_path VARCHAR(500),created_at DATETIME NOT NULL,updated_at DATETIME NOT NULL,INDEX idx_writing_project_user_created (user_id, created_at),INDEX idx_writing_project_status (status)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                "CREATE TABLE IF NOT EXISTS writing_chapter (id VARCHAR(64) PRIMARY KEY,project_id VARCHAR(64) NOT NULL,chapter_no INT NOT NULL,title VARCHAR(255) NOT NULL,target_word_count INT NOT NULL DEFAULT 1200,section_count INT NOT NULL DEFAULT 3,image_count INT NOT NULL DEFAULT 1,table_count INT NOT NULL DEFAULT 1,use_references TINYINT(1) NOT NULL DEFAULT 1,default_chart_type VARCHAR(60) DEFAULT 'COMBO',content LONGTEXT,chapter_summary TEXT,status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',sort_order INT NOT NULL DEFAULT 0,created_at DATETIME NOT NULL,updated_at DATETIME NOT NULL,UNIQUE KEY uk_writing_chapter_no (project_id, chapter_no),INDEX idx_writing_chapter_project_sort (project_id, sort_order)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                "CREATE TABLE IF NOT EXISTS writing_section (id VARCHAR(64) PRIMARY KEY,project_id VARCHAR(64) NOT NULL,chapter_id VARCHAR(64) NOT NULL,section_no VARCHAR(40) NOT NULL,title VARCHAR(255) NOT NULL,target_word_count INT NOT NULL DEFAULT 400,content LONGTEXT,summary TEXT,sort_order INT NOT NULL DEFAULT 0,status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',created_at DATETIME NOT NULL,updated_at DATETIME NOT NULL,UNIQUE KEY uk_writing_section_no (chapter_id, section_no),INDEX idx_writing_section_project (project_id),INDEX idx_writing_section_chapter_sort (chapter_id, sort_order)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                "CREATE TABLE IF NOT EXISTS writing_chart (id VARCHAR(64) PRIMARY KEY,project_id VARCHAR(64) NOT NULL,chapter_id VARCHAR(64) NOT NULL,chart_no VARCHAR(40) NOT NULL,title VARCHAR(255) NOT NULL,chart_type VARCHAR(60) NOT NULL,source_type VARCHAR(60) NOT NULL DEFAULT 'SIMULATED',source_name VARCHAR(255),source_url VARCHAR(500),source_date DATETIME,is_simulated TINYINT(1) NOT NULL DEFAULT 1,x_axis_name VARCHAR(120),y_axis_name VARCHAR(120),secondary_y_axis_name VARCHAR(120),show_legend TINYINT(1) NOT NULL DEFAULT 1,show_data_label TINYINT(1) NOT NULL DEFAULT 0,show_axis_title TINYINT(1) NOT NULL DEFAULT 1,use_secondary_axis TINYINT(1) NOT NULL DEFAULT 0,stacked TINYINT(1) NOT NULL DEFAULT 0,show_trendline TINYINT(1) NOT NULL DEFAULT 0,chart_config_json TEXT,data_json LONGTEXT,image_path VARCHAR(500),insert_after_section VARCHAR(64),description TEXT,sort_order INT NOT NULL DEFAULT 0,created_at DATETIME NOT NULL,updated_at DATETIME NOT NULL,UNIQUE KEY uk_writing_chart_no (project_id, chart_no),INDEX idx_writing_chart_chapter_sort (chapter_id, sort_order)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                "CREATE TABLE IF NOT EXISTS writing_chart_series (id VARCHAR(64) PRIMARY KEY,chart_id VARCHAR(64) NOT NULL,series_name VARCHAR(120) NOT NULL,chart_type VARCHAR(60) NOT NULL,use_secondary_axis TINYINT(1) NOT NULL DEFAULT 0,unit VARCHAR(40),source_type VARCHAR(60) DEFAULT 'SIMULATED',data_json LONGTEXT,sort_order INT NOT NULL DEFAULT 0,created_at DATETIME NOT NULL,updated_at DATETIME NOT NULL,INDEX idx_writing_chart_series_chart_sort (chart_id, sort_order)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                "CREATE TABLE IF NOT EXISTS writing_table (id VARCHAR(64) PRIMARY KEY,project_id VARCHAR(64) NOT NULL,chapter_id VARCHAR(64) NOT NULL,table_no VARCHAR(40) NOT NULL,title VARCHAR(255) NOT NULL,table_type VARCHAR(80) NOT NULL DEFAULT 'INDICATOR_STAT',source_type VARCHAR(60) NOT NULL DEFAULT 'SIMULATED',source_name VARCHAR(255),source_url VARCHAR(500),is_simulated TINYINT(1) NOT NULL DEFAULT 1,header_json TEXT,rows_json LONGTEXT,use_three_line_style TINYINT(1) NOT NULL DEFAULT 1,note TEXT,insert_after_section VARCHAR(64),sort_order INT NOT NULL DEFAULT 0,created_at DATETIME NOT NULL,updated_at DATETIME NOT NULL,UNIQUE KEY uk_writing_table_no (project_id, table_no),INDEX idx_writing_table_chapter_sort (chapter_id, sort_order)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                "CREATE TABLE IF NOT EXISTS writing_reference (id VARCHAR(64) PRIMARY KEY,project_id VARCHAR(64) NOT NULL,reference_key VARCHAR(80) NOT NULL,title VARCHAR(500) NOT NULL,authors TEXT NOT NULL,publication_year INT NOT NULL,journal_or_publisher VARCHAR(500),volume VARCHAR(80),issue VARCHAR(80),pages VARCHAR(120),doi VARCHAR(255),url VARCHAR(700),source_platform VARCHAR(120) NOT NULL,abstract_text LONGTEXT,search_keywords VARCHAR(500),searched_at DATETIME NOT NULL,applicable_chapters VARCHAR(255),verification_status VARCHAR(40) NOT NULL,relevance_score DECIMAL(8,4) NOT NULL DEFAULT 0,formatted_text TEXT,final_number INT,created_at DATETIME NOT NULL,updated_at DATETIME NOT NULL,UNIQUE KEY uk_writing_reference_key (project_id, reference_key),INDEX idx_writing_reference_project_score (project_id, relevance_score),INDEX idx_writing_reference_doi (doi)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                "CREATE TABLE IF NOT EXISTS writing_citation (id VARCHAR(64) PRIMARY KEY,project_id VARCHAR(64) NOT NULL,chapter_id VARCHAR(64),reference_id VARCHAR(64) NOT NULL,temporary_marker VARCHAR(120) NOT NULL,final_number INT,first_occurrence_order INT,context_text TEXT,created_at DATETIME NOT NULL,updated_at DATETIME NOT NULL,INDEX idx_writing_citation_project_order (project_id, first_occurrence_order),INDEX idx_writing_citation_reference (reference_id)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                "CREATE TABLE IF NOT EXISTS writing_generation_task (id VARCHAR(64) PRIMARY KEY,project_id VARCHAR(64) NOT NULL,user_id BIGINT NOT NULL,task_type VARCHAR(60) NOT NULL,status VARCHAR(30) NOT NULL DEFAULT 'PENDING',stage VARCHAR(120),progress INT NOT NULL DEFAULT 0,error_message TEXT,retryable TINYINT(1) NOT NULL DEFAULT 1,completed_chapters TEXT,generated_files TEXT,created_at DATETIME NOT NULL,updated_at DATETIME NOT NULL,INDEX idx_writing_task_project (project_id),INDEX idx_writing_task_user_created (user_id, created_at)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                "CREATE TABLE IF NOT EXISTS writing_export_file (id VARCHAR(64) PRIMARY KEY,project_id VARCHAR(64) NOT NULL,task_id VARCHAR(64),document_job_id VARCHAR(64),file_name VARCHAR(255) NOT NULL,file_type VARCHAR(20) NOT NULL,file_path VARCHAR(500),file_size BIGINT NOT NULL DEFAULT 0,download_url VARCHAR(500),created_at DATETIME NOT NULL,INDEX idx_writing_export_project (project_id)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                "INSERT INTO feature_pricing (feature_code, feature_name, cost_points, enabled) SELECT 'WRITING_DOCX', '纯文字稿生成', 60, 1 WHERE NOT EXISTS (SELECT 1 FROM feature_pricing WHERE feature_code = 'WRITING_DOCX')"
        );
    }

    private List<String> h2Statements() {
        return mysqlStatements().stream()
                .map(sql -> sql.replace("LONGTEXT", "CLOB")
                        .replace("TEXT", "CLOB")
                        .replace("DATETIME", "TIMESTAMP")
                        .replace("TINYINT(1)", "BOOLEAN")
                        .replaceAll("DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", "")
                        .replaceAll(",INDEX [^)]+\\)", ")")
                        .replaceAll(",UNIQUE KEY [^)]+\\)", ")")
                        .replace("UNIQUE KEY", "UNIQUE")
                        .replace("KEY", ""))
                .toList();
    }

    private void safeExecute(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (DataAccessException exception) {
            String message = String.valueOf(exception.getMostSpecificCause()).toLowerCase();
            if (message.contains("already exists") || message.contains("duplicate") || message.contains("syntax error in sql statement") && sql.startsWith("INSERT INTO feature_pricing")) {
                return;
            }
            throw exception;
        }
    }
}
