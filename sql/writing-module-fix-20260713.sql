SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS writing_request_dedup (
  request_id VARCHAR(80) PRIMARY KEY,
  project_id VARCHAR(64) NOT NULL,
  request_type VARCHAR(60) NOT NULL,
  client_revision BIGINT,
  result_revision BIGINT,
  response_json LONGTEXT,
  created_at DATETIME NOT NULL,
  INDEX idx_writing_request_project (project_id, request_type, created_at)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @db := DATABASE();

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_project' AND COLUMN_NAME='chinese_reference_count')=0,
  'ALTER TABLE writing_project ADD COLUMN chinese_reference_count INT NOT NULL DEFAULT 14',
  'SELECT ''chinese_reference_count exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_project' AND COLUMN_NAME='english_reference_count')=0,
  'ALTER TABLE writing_project ADD COLUMN english_reference_count INT NOT NULL DEFAULT 6',
  'SELECT ''english_reference_count exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE writing_project
SET chinese_reference_count = ROUND(reference_count * 0.7),
    english_reference_count = reference_count - ROUND(reference_count * 0.7)
WHERE reference_count IS NOT NULL
  AND (chinese_reference_count IS NULL OR chinese_reference_count = 8)
  AND (english_reference_count IS NULL OR english_reference_count = 12);

SET @cols := 'journal:VARCHAR(500),publisher:VARCHAR(500),source_url:VARCHAR(700),landing_page_url:VARCHAR(700),language:VARCHAR(20) NOT NULL DEFAULT ''UNKNOWN'',provider:VARCHAR(80),provider_record_id:VARCHAR(255),verified_at:DATETIME,citation_number:INT,raw_metadata:LONGTEXT';

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='journal')=0,
  'ALTER TABLE writing_reference ADD COLUMN journal VARCHAR(500)', 'SELECT ''journal exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='publisher')=0,
  'ALTER TABLE writing_reference ADD COLUMN publisher VARCHAR(500)', 'SELECT ''publisher exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='source_url')=0,
  'ALTER TABLE writing_reference ADD COLUMN source_url VARCHAR(700)', 'SELECT ''source_url exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='landing_page_url')=0,
  'ALTER TABLE writing_reference ADD COLUMN landing_page_url VARCHAR(700)', 'SELECT ''landing_page_url exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='language')=0,
  'ALTER TABLE writing_reference ADD COLUMN language VARCHAR(20) NOT NULL DEFAULT ''UNKNOWN''', 'SELECT ''language exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='provider')=0,
  'ALTER TABLE writing_reference ADD COLUMN provider VARCHAR(80)', 'SELECT ''provider exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='provider_record_id')=0,
  'ALTER TABLE writing_reference ADD COLUMN provider_record_id VARCHAR(255)', 'SELECT ''provider_record_id exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='verified_at')=0,
  'ALTER TABLE writing_reference ADD COLUMN verified_at DATETIME', 'SELECT ''verified_at exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='citation_number')=0,
  'ALTER TABLE writing_reference ADD COLUMN citation_number INT', 'SELECT ''citation_number exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND COLUMN_NAME='raw_metadata')=0,
  'ALTER TABLE writing_reference ADD COLUMN raw_metadata LONGTEXT', 'SELECT ''raw_metadata exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE writing_reference
SET language = CASE
  WHEN title REGEXP '[一-龥]' THEN 'ZH'
  WHEN language IS NULL OR language = '' THEN 'EN'
  ELSE language
END,
provider = COALESCE(provider, UPPER(REPLACE(source_platform, '-', '_'))),
source_url = COALESCE(source_url, url),
landing_page_url = COALESCE(landing_page_url, url),
citation_number = COALESCE(citation_number, final_number)
WHERE project_id IS NOT NULL;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND INDEX_NAME='idx_writing_reference_language')=0,
  'CREATE INDEX idx_writing_reference_language ON writing_reference (project_id, language)', 'SELECT ''idx language exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND INDEX_NAME='idx_writing_reference_citation')=0,
  'CREATE INDEX idx_writing_reference_citation ON writing_reference (project_id, citation_number)', 'SELECT ''idx citation exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='writing_reference' AND INDEX_NAME='idx_writing_reference_provider_record')=0,
  'CREATE INDEX idx_writing_reference_provider_record ON writing_reference (provider, provider_record_id)', 'SELECT ''idx provider exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
