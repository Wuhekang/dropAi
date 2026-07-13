SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS writing_reference_source_evidence (
  id VARCHAR(64) PRIMARY KEY,
  reference_id VARCHAR(64) NOT NULL,
  project_id VARCHAR(64) NOT NULL,
  provider VARCHAR(80) NOT NULL,
  source_type VARCHAR(80) NOT NULL,
  source_title VARCHAR(500),
  source_url VARCHAR(1000) NOT NULL,
  source_domain VARCHAR(255),
  source_snippet TEXT,
  query_text TEXT,
  retrieved_at DATETIME,
  created_at DATETIME NOT NULL,
  INDEX idx_wrse_reference (reference_id),
  INDEX idx_wrse_project_created (project_id, created_at),
  INDEX idx_wrse_domain (source_domain)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS writing_reference_search_log (
  id VARCHAR(64) PRIMARY KEY,
  project_id VARCHAR(64) NOT NULL,
  chapter_id VARCHAR(64),
  provider VARCHAR(80) NOT NULL,
  language VARCHAR(20),
  query_text TEXT,
  request_api_type VARCHAR(80),
  request_method VARCHAR(20),
  request_url VARCHAR(700),
  model VARCHAR(120),
  web_search_enabled TINYINT(1) NOT NULL DEFAULT 0,
  http_status INT,
  result_count INT NOT NULL DEFAULT 0,
  accepted_count INT NOT NULL DEFAULT 0,
  rejected_count INT NOT NULL DEFAULT 0,
  duration_ms BIGINT NOT NULL DEFAULT 0,
  success TINYINT(1) NOT NULL DEFAULT 0,
  error_code VARCHAR(120),
  error_message TEXT,
  created_at DATETIME NOT NULL,
  INDEX idx_wrsl_project_created (project_id, created_at),
  INDEX idx_wrsl_provider_language (provider, language)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @db := DATABASE();

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='language')=0,
  'ALTER TABLE writing_reference ADD COLUMN language VARCHAR(20)', 'SELECT ''language exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='document_type')=0,
  'ALTER TABLE writing_reference ADD COLUMN document_type VARCHAR(80)', 'SELECT ''document_type exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='source_database')=0,
  'ALTER TABLE writing_reference ADD COLUMN source_database VARCHAR(120)', 'SELECT ''source_database exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='external_record_id')=0,
  'ALTER TABLE writing_reference ADD COLUMN external_record_id VARCHAR(255)', 'SELECT ''external_record_id exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='source_query')=0,
  'ALTER TABLE writing_reference ADD COLUMN source_query TEXT', 'SELECT ''source_query exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='source_url')=0,
  'ALTER TABLE writing_reference ADD COLUMN source_url VARCHAR(1000)', 'SELECT ''source_url exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='retrieved_at')=0,
  'ALTER TABLE writing_reference ADD COLUMN retrieved_at DATETIME', 'SELECT ''retrieved_at exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='abstract_source_type')=0,
  'ALTER TABLE writing_reference ADD COLUMN abstract_source_type VARCHAR(80)', 'SELECT ''abstract_source_type exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='verification_message')=0,
  'ALTER TABLE writing_reference ADD COLUMN verification_message TEXT', 'SELECT ''verification_message exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='format_incomplete')=0,
  'ALTER TABLE writing_reference ADD COLUMN format_incomplete TINYINT(1) NOT NULL DEFAULT 0', 'SELECT ''format_incomplete exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='missing_fields_json')=0,
  'ALTER TABLE writing_reference ADD COLUMN missing_fields_json LONGTEXT', 'SELECT ''missing_fields_json exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='metadata_conflicts_json')=0,
  'ALTER TABLE writing_reference ADD COLUMN metadata_conflicts_json LONGTEXT', 'SELECT ''metadata_conflicts_json exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='source_evidence_json')=0,
  'ALTER TABLE writing_reference ADD COLUMN source_evidence_json LONGTEXT', 'SELECT ''source_evidence_json exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='raw_metadata_json')=0,
  'ALTER TABLE writing_reference ADD COLUMN raw_metadata_json LONGTEXT', 'SELECT ''raw_metadata_json exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND INDEX_NAME='idx_wr_language_status')=0,
  'CREATE INDEX idx_wr_language_status ON writing_reference (project_id, language, verification_status)', 'SELECT ''idx_wr_language_status exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND INDEX_NAME='idx_wr_external_record')=0,
  'CREATE INDEX idx_wr_external_record ON writing_reference (source_database, external_record_id)', 'SELECT ''idx_wr_external_record exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
