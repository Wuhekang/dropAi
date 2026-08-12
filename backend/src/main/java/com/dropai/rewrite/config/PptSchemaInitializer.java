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
public class PptSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    public PptSchemaInitializer(JdbcTemplate jdbc, DataSource dataSource) { this.jdbc = jdbc; this.dataSource = dataSource; }

    @Override public void run(ApplicationArguments args) throws Exception {
        boolean h2;
        try (Connection c = dataSource.getConnection()) { h2 = c.getMetaData().getDatabaseProductName().toLowerCase().contains("h2"); }
        for (String sql : statements(h2)) try { jdbc.execute(sql); } catch (DataAccessException e) {
            String m = String.valueOf(e.getMostSpecificCause()).toLowerCase();
            if (!m.contains("already exists") && !m.contains("duplicate")) throw e;
        }
        ensureColumn("ppt_project","template_style","ALTER TABLE ppt_project ADD COLUMN template_style VARCHAR(40) NOT NULL DEFAULT 'AI_RECOMMEND'");
        ensureColumn("ppt_project","template_id","ALTER TABLE ppt_project ADD COLUMN template_id VARCHAR(64)");
        ensureColumn("ppt_project","template_metadata_json","ALTER TABLE ppt_project ADD COLUMN template_metadata_json "+(h2?"CLOB":"LONGTEXT"));
        ensureColumn("ppt_slide","chapter_title","ALTER TABLE ppt_slide ADD COLUMN chapter_title VARCHAR(255)");
        ensureColumn("ppt_slide","content_summary","ALTER TABLE ppt_slide ADD COLUMN content_summary "+(h2?"CLOB":"LONGTEXT"));
        ensureColumn("ppt_slide","template_type","ALTER TABLE ppt_slide ADD COLUMN template_type VARCHAR(60)");
    }

    private void ensureColumn(String table,String column,String ddl) throws Exception {
        try(Connection c=dataSource.getConnection();var rs=c.getMetaData().getColumns(c.getCatalog(),null,table,column)){if(rs.next())return;}
        jdbc.execute(ddl);
    }

    private List<String> statements(boolean h2) {
        String text = h2 ? "CLOB" : "LONGTEXT";
        String time = h2 ? "TIMESTAMP" : "DATETIME";
        String suffix = h2 ? "" : " DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        return List.of(
            "CREATE TABLE IF NOT EXISTS ppt_project (id VARCHAR(64) PRIMARY KEY,user_id BIGINT NOT NULL,topic VARCHAR(255),english_topic VARCHAR(255),presenter VARCHAR(120),major VARCHAR(120),advisor VARCHAR(120),student_number VARCHAR(80),source_file_path VARCHAR(700),source_file_name VARCHAR(255),source_file_size BIGINT NOT NULL DEFAULT 0,target_slide_count INT NOT NULL DEFAULT 16,status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',current_stage VARCHAR(120),progress INT NOT NULL DEFAULT 0,analysis_json "+text+",output_path VARCHAR(700),error_message "+text+",created_at "+time+" NOT NULL,updated_at "+time+" NOT NULL)"+suffix,
            "CREATE TABLE IF NOT EXISTS ppt_outline (id VARCHAR(64) PRIMARY KEY,project_id VARCHAR(64) NOT NULL,section_order INT NOT NULL,title VARCHAR(255) NOT NULL,description "+text+",target_slides INT NOT NULL DEFAULT 2)"+suffix,
            "CREATE TABLE IF NOT EXISTS ppt_slide (id VARCHAR(64) PRIMARY KEY,project_id VARCHAR(64) NOT NULL,section_id VARCHAR(64),slide_order INT NOT NULL,slide_type VARCHAR(40) NOT NULL,title VARCHAR(255) NOT NULL,body_boxes_json "+text+",asset_ids_json "+text+",speaker_notes "+text+",layout_type VARCHAR(60),validation_status VARCHAR(40) NOT NULL DEFAULT 'PENDING')"+suffix,
            "CREATE TABLE IF NOT EXISTS ppt_asset (id VARCHAR(64) PRIMARY KEY,project_id VARCHAR(64) NOT NULL,source_type VARCHAR(40) NOT NULL,source_page INT,source_position VARCHAR(120),file_path VARCHAR(700) NOT NULL,caption VARCHAR(500),width INT,height INT,created_at "+time+" NOT NULL)"+suffix,
            "CREATE TABLE IF NOT EXISTS ppt_generation_task (id VARCHAR(64) PRIMARY KEY,project_id VARCHAR(64) NOT NULL,user_id BIGINT NOT NULL,status VARCHAR(40) NOT NULL,progress INT NOT NULL DEFAULT 0,current_stage VARCHAR(120),error_message "+text+",created_at "+time+" NOT NULL,updated_at "+time+" NOT NULL)"+suffix,
            "CREATE TABLE IF NOT EXISTS ppt_template (id VARCHAR(64) PRIMARY KEY,user_id BIGINT NOT NULL,template_name VARCHAR(255) NOT NULL,style VARCHAR(40) NOT NULL,suitable_major VARCHAR(255),slide_types_json "+text+",metadata_json "+text+",file_path VARCHAR(700) NOT NULL,status VARCHAR(40) NOT NULL DEFAULT 'READY',created_at "+time+" NOT NULL,updated_at "+time+" NOT NULL)"+suffix,
            "INSERT INTO feature_pricing (feature_code,feature_name,cost_points,enabled) SELECT 'PPT_GENERATE','PPT智能生成',100,1 WHERE NOT EXISTS (SELECT 1 FROM feature_pricing WHERE feature_code='PPT_GENERATE')"
        );
    }
}
