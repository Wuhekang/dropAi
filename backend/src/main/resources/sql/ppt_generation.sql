CREATE TABLE IF NOT EXISTS ppt_project (
  id VARCHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL, topic VARCHAR(255), english_topic VARCHAR(255),
  presenter VARCHAR(120), major VARCHAR(120), advisor VARCHAR(120), student_number VARCHAR(80),
  source_file_path VARCHAR(700), source_file_name VARCHAR(255), source_file_size BIGINT NOT NULL DEFAULT 0,
  target_slide_count INT NOT NULL DEFAULT 16, status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
  current_stage VARCHAR(120), progress INT NOT NULL DEFAULT 0, analysis_json LONGTEXT,
  output_path VARCHAR(700), error_message LONGTEXT, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
  INDEX idx_ppt_project_user_created (user_id, created_at), INDEX idx_ppt_project_status (status)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ppt_outline (id VARCHAR(64) PRIMARY KEY, project_id VARCHAR(64) NOT NULL, section_order INT NOT NULL, title VARCHAR(255) NOT NULL, description LONGTEXT, target_slides INT NOT NULL DEFAULT 2, INDEX idx_ppt_outline_project_order(project_id,section_order)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS ppt_slide (id VARCHAR(64) PRIMARY KEY, project_id VARCHAR(64) NOT NULL, section_id VARCHAR(64), slide_order INT NOT NULL, slide_type VARCHAR(40) NOT NULL, title VARCHAR(255) NOT NULL, body_boxes_json LONGTEXT, asset_ids_json LONGTEXT, speaker_notes LONGTEXT, layout_type VARCHAR(60), validation_status VARCHAR(40) NOT NULL DEFAULT 'PENDING', INDEX idx_ppt_slide_project_order(project_id,slide_order)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS ppt_asset (id VARCHAR(64) PRIMARY KEY, project_id VARCHAR(64) NOT NULL, source_type VARCHAR(40) NOT NULL, source_page INT, source_position VARCHAR(120), file_path VARCHAR(700) NOT NULL, caption VARCHAR(500), width INT, height INT, created_at DATETIME NOT NULL, INDEX idx_ppt_asset_project_page(project_id,source_page)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS ppt_generation_task (id VARCHAR(64) PRIMARY KEY, project_id VARCHAR(64) NOT NULL, user_id BIGINT NOT NULL, status VARCHAR(40) NOT NULL, progress INT NOT NULL DEFAULT 0, current_stage VARCHAR(120), error_message LONGTEXT, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, INDEX idx_ppt_task_project(project_id), INDEX idx_ppt_task_user_created(user_id,created_at)) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO feature_pricing(feature_code,feature_name,cost_points,enabled) SELECT 'PPT_GENERATE','PPT智能生成',100,1 WHERE NOT EXISTS (SELECT 1 FROM feature_pricing WHERE feature_code='PPT_GENERATE');
