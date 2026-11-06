package com.dropai.rewrite.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
public class DiagramPreviewSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public DiagramPreviewSchemaInitializer(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc; this.dataSource = dataSource;
    }

    @Override public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            boolean h2 = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("h2");
            create(h2);
        } catch (Exception exception) {
            throw new IllegalStateException("智能画图计费表初始化失败，收费预览已停止启动", exception);
        }
    }

    private void create(boolean h2) {
        String id = h2 ? "VARCHAR(64) PRIMARY KEY" : "VARCHAR(64) PRIMARY KEY";
        String time = h2 ? "TIMESTAMP" : "DATETIME";
        String text = h2 ? "CLOB" : "MEDIUMTEXT";
        String suffix = h2 ? "" : " DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        String projectId = h2 ? "BIGINT AUTO_INCREMENT PRIMARY KEY" : "BIGINT PRIMARY KEY AUTO_INCREMENT";
        jdbc.execute("CREATE TABLE IF NOT EXISTS diagram_project (id "+projectId+",user_id BIGINT NOT NULL,title VARCHAR(120) NOT NULL,diagram_type VARCHAR(32) NOT NULL,dsl_version VARCHAR(16) NOT NULL DEFAULT '1.6',source_dsl "+text+" NOT NULL,latest_valid_dsl "+text+",created_at "+time+" NOT NULL,updated_at "+time+" NOT NULL)"+suffix);
        jdbc.execute("CREATE TABLE IF NOT EXISTS diagram_render_task (id "+id+",user_id BIGINT NOT NULL,project_id BIGINT NOT NULL,diagram_type VARCHAR(32) NOT NULL,render_hash VARCHAR(64) NOT NULL,renderer_version VARCHAR(64) NOT NULL,status VARCHAR(20) NOT NULL,error_message VARCHAR(500),charged_points INT NOT NULL DEFAULT 0,charge_transaction_id BIGINT,refund_transaction_id BIGINT,created_at "+time+" NOT NULL,updated_at "+time+" NOT NULL,UNIQUE(user_id,project_id,render_hash))"+suffix);
        jdbc.execute("CREATE TABLE IF NOT EXISTS diagram_preview (id "+id+",task_id VARCHAR(64) NOT NULL,user_id BIGINT NOT NULL,project_id BIGINT NOT NULL,diagram_type VARCHAR(32) NOT NULL,render_hash VARCHAR(64) NOT NULL,renderer_version VARCHAR(64) NOT NULL,status VARCHAR(20) NOT NULL,charged_points INT NOT NULL DEFAULT 0,charge_transaction_id BIGINT,refund_transaction_id BIGINT,normalized_dsl "+text+",svg_content "+text+",created_at "+time+" NOT NULL,updated_at "+time+" NOT NULL,UNIQUE(user_id,project_id,render_hash))"+suffix);
        try{jdbc.execute("ALTER TABLE diagram_preview ADD COLUMN normalized_dsl "+text);}catch(Exception duplicate){if(!duplicateColumn(duplicate))throw duplicate;}
        jdbc.execute("CREATE TABLE IF NOT EXISTS diagram_artifact (id "+id+",preview_id VARCHAR(64) NOT NULL,format VARCHAR(12) NOT NULL,status VARCHAR(20) NOT NULL,file_path VARCHAR(600),file_size BIGINT NOT NULL DEFAULT 0,failure_reason VARCHAR(255),created_at "+time+" NOT NULL,updated_at "+time+" NOT NULL,UNIQUE(preview_id,format))"+suffix);
        jdbc.execute("CREATE TABLE IF NOT EXISTS diagram_preview_charge (id "+id+",task_id VARCHAR(64) NOT NULL,preview_id VARCHAR(64),user_id BIGINT NOT NULL,project_id BIGINT NOT NULL,render_hash VARCHAR(64) NOT NULL,kind VARCHAR(12) NOT NULL,points INT NOT NULL,transaction_id BIGINT,status VARCHAR(20) NOT NULL,related_charge_id VARCHAR(64),created_at "+time+" NOT NULL,UNIQUE(task_id,kind))"+suffix);
    }
    private static boolean duplicateColumn(Throwable error){for(Throwable e=error;e!=null;e=e.getCause()){String m=String.valueOf(e.getMessage()).toLowerCase();if(m.contains("duplicate column")||m.contains("already exists"))return true;}return false;}
}
