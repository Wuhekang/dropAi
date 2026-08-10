-- DropAI 纯文字稿 V2 数据库升级脚本
-- 适用：MySQL 5.7 / 8.0
-- 特点：可重复执行；仅补充缺失字段和索引，不删除现有数据。
-- 使用前请先选择 DropAI 数据库，例如：USE drop_ai;

SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS dropai_add_column_if_missing$$
CREATE PROCEDURE dropai_add_column_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_definition TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = p_table_name
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        SET @dropai_sql = CONCAT(
            'ALTER TABLE `', REPLACE(p_table_name, '`', '``'),
            '` ADD COLUMN `', REPLACE(p_column_name, '`', '``'),
            '` ', p_column_definition
        );
        PREPARE dropai_stmt FROM @dropai_sql;
        EXECUTE dropai_stmt;
        DEALLOCATE PREPARE dropai_stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS dropai_add_index_if_missing$$
CREATE PROCEDURE dropai_add_index_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_index_columns TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = p_table_name
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND index_name = p_index_name
    ) THEN
        SET @dropai_sql = CONCAT(
            'CREATE INDEX `', REPLACE(p_index_name, '`', '``'),
            '` ON `', REPLACE(p_table_name, '`', '``'), '` (', p_index_columns, ')'
        );
        PREPARE dropai_stmt FROM @dropai_sql;
        EXECUTE dropai_stmt;
        DEALLOCATE PREPARE dropai_stmt;
    END IF;
END$$

DELIMITER ;

-- V2 新增的文献检索证据、检索日志和导入批次表。
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

CREATE TABLE IF NOT EXISTS writing_reference_import_batch (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    source_platform VARCHAR(80) NOT NULL,
    original_filename VARCHAR(255),
    stored_filename VARCHAR(255),
    file_format VARCHAR(40),
    file_encoding VARCHAR(40),
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    duplicate_count INT NOT NULL DEFAULT 0,
    status VARCHAR(40) NOT NULL,
    error_message TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_wrib_project_created (project_id, created_at),
    INDEX idx_wrib_user_created (user_id, created_at)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1. 项目：文献语言目标、文献来源模式、专业设计信息。
CALL dropai_add_column_if_missing('writing_project', 'chinese_reference_count', "INT NOT NULL DEFAULT 14");
CALL dropai_add_column_if_missing('writing_project', 'english_reference_count', "INT NOT NULL DEFAULT 6");
CALL dropai_add_column_if_missing('writing_project', 'document_mode', "VARCHAR(40) NOT NULL DEFAULT 'general'");
CALL dropai_add_column_if_missing('writing_project', 'project_location', "VARCHAR(500) NULL");
CALL dropai_add_column_if_missing('writing_project', 'reference_mode', "VARCHAR(20) NOT NULL DEFAULT 'AI'");

-- 2. 章节：区分普通正文、结论、参考文献和致谢。
CALL dropai_add_column_if_missing('writing_chapter', 'chapter_type', "VARCHAR(40) NOT NULL DEFAULT 'content'");

UPDATE writing_chapter
SET chapter_type = 'reference'
WHERE LOWER(TRIM(title)) IN ('references', 'reference')
   OR title LIKE '%参考文献%';

UPDATE writing_chapter
SET chapter_type = 'acknowledgement'
WHERE LOWER(TRIM(title)) IN ('acknowledgement', 'acknowledgements', 'acknowledgment', 'acknowledgments')
   OR title LIKE '%致谢%';

UPDATE writing_chapter
SET chapter_type = 'conclusion'
WHERE chapter_type = 'content'
  AND (title LIKE '%结论%' OR title LIKE '%展望%' OR title LIKE '%总结%');

-- 3. 小节：内容类型、图表数量、图片策略与需求配置。
CALL dropai_add_column_if_missing('writing_section', 'content_type', "VARCHAR(40) NOT NULL DEFAULT 'general'");
CALL dropai_add_column_if_missing('writing_section', 'image_count', "INT NOT NULL DEFAULT 0");
CALL dropai_add_column_if_missing('writing_section', 'table_count', "INT NOT NULL DEFAULT 0");
CALL dropai_add_column_if_missing('writing_section', 'image_strategy', "VARCHAR(40) NOT NULL DEFAULT 'none'");
CALL dropai_add_column_if_missing('writing_section', 'image_requirements_json', "LONGTEXT NULL");

-- 4. 图片素材：用户确认状态及同一小节内的展示顺序。
CALL dropai_add_column_if_missing('writing_image_material', 'is_confirmed', "TINYINT(1) NOT NULL DEFAULT 0");
CALL dropai_add_column_if_missing('writing_image_material', 'display_order', "INT NOT NULL DEFAULT 0");

-- 将升级前已经完成章节、小节绑定的图片视为已确认。
UPDATE writing_image_material
SET is_confirmed = 1
WHERE user_confirmed_chapter IS NOT NULL
  AND user_confirmed_chapter <> ''
  AND user_confirmed_section IS NOT NULL
  AND user_confirmed_section <> '';

CALL dropai_add_index_if_missing(
    'writing_image_material',
    'idx_wim_confirmed_section',
    '`project_id`, `is_confirmed`, `user_confirmed_section`'
);

-- 5. 表格：用户最终确认的小节位置及确认状态。
CALL dropai_add_column_if_missing('writing_table', 'user_confirmed_section', "VARCHAR(64) NULL");
CALL dropai_add_column_if_missing('writing_table', 'is_confirmed', "TINYINT(1) NOT NULL DEFAULT 0");

-- 兼容旧数据：原 insert_after_section 即升级后的确认小节。
UPDATE writing_table
SET user_confirmed_section = insert_after_section,
    is_confirmed = 1
WHERE insert_after_section IS NOT NULL
  AND insert_after_section <> ''
  AND (user_confirmed_section IS NULL OR user_confirmed_section = '');

CALL dropai_add_index_if_missing(
    'writing_table',
    'idx_writing_table_confirmed_section',
    '`project_id`, `is_confirmed`, `user_confirmed_section`'
);

-- 6. 参考文献：语言、来源类型、Provider 元数据、核验信息和引用编号。
CALL dropai_add_column_if_missing('writing_reference', 'journal', "VARCHAR(500) NULL");
CALL dropai_add_column_if_missing('writing_reference', 'publisher', "VARCHAR(500) NULL");
CALL dropai_add_column_if_missing('writing_reference', 'source_url', "VARCHAR(700) NULL");
CALL dropai_add_column_if_missing('writing_reference', 'landing_page_url', "VARCHAR(700) NULL");
CALL dropai_add_column_if_missing('writing_reference', 'language', "VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN'");
CALL dropai_add_column_if_missing('writing_reference', 'source_type', "VARCHAR(40) NOT NULL DEFAULT 'AI_SEARCH'");
CALL dropai_add_column_if_missing('writing_reference', 'provider', "VARCHAR(80) NULL");
CALL dropai_add_column_if_missing('writing_reference', 'provider_record_id', "VARCHAR(255) NULL");
CALL dropai_add_column_if_missing('writing_reference', 'verified_at', "DATETIME NULL");
CALL dropai_add_column_if_missing('writing_reference', 'citation_number', "INT NULL");
CALL dropai_add_column_if_missing('writing_reference', 'raw_metadata', "LONGTEXT NULL");
CALL dropai_add_column_if_missing('writing_reference', 'document_type', "VARCHAR(80) NULL");
CALL dropai_add_column_if_missing('writing_reference', 'institution', "VARCHAR(255) NULL");
CALL dropai_add_column_if_missing('writing_reference', 'source_database', "VARCHAR(120) NULL");
CALL dropai_add_column_if_missing('writing_reference', 'external_record_id', "VARCHAR(255) NULL");
CALL dropai_add_column_if_missing('writing_reference', 'source_query', "TEXT NULL");
CALL dropai_add_column_if_missing('writing_reference', 'retrieved_at', "DATETIME NULL");
CALL dropai_add_column_if_missing('writing_reference', 'abstract_source_type', "VARCHAR(80) NULL");
CALL dropai_add_column_if_missing('writing_reference', 'verification_message', "TEXT NULL");
CALL dropai_add_column_if_missing('writing_reference', 'format_incomplete', "TINYINT(1) NOT NULL DEFAULT 0");
CALL dropai_add_column_if_missing('writing_reference', 'missing_fields_json', "LONGTEXT NULL");
CALL dropai_add_column_if_missing('writing_reference', 'metadata_conflicts_json', "LONGTEXT NULL");
CALL dropai_add_column_if_missing('writing_reference', 'raw_metadata_json', "LONGTEXT NULL");
CALL dropai_add_column_if_missing('writing_reference', 'source_evidence_json', "LONGTEXT NULL");

UPDATE writing_reference
SET source_type = 'AI_SEARCH'
WHERE source_type IS NULL OR source_type = '';

CALL dropai_add_index_if_missing('writing_reference', 'idx_writing_reference_language', '`project_id`, `language`');
CALL dropai_add_index_if_missing('writing_reference', 'idx_writing_reference_citation', '`project_id`, `citation_number`');
CALL dropai_add_index_if_missing('writing_reference', 'idx_writing_reference_provider_record', '`provider`, `provider_record_id`');

-- 7. DOCX 功能计价项；已有记录时不会重复写入。
INSERT INTO feature_pricing (feature_code, feature_name, cost_points, enabled)
SELECT 'WRITING_DOCX', '纯文字稿生成', 60, 1
WHERE EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'feature_pricing'
)
AND NOT EXISTS (
    SELECT 1 FROM feature_pricing WHERE feature_code = 'WRITING_DOCX'
);

-- 清理本次脚本使用的临时存储过程。
DROP PROCEDURE IF EXISTS dropai_add_column_if_missing;
DROP PROCEDURE IF EXISTS dropai_add_index_if_missing;

-- 升级结果检查。
SELECT table_name, column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND (
      (table_name = 'writing_project' AND column_name IN
       ('chinese_reference_count','english_reference_count','document_mode','project_location','reference_mode'))
   OR (table_name = 'writing_chapter' AND column_name = 'chapter_type')
   OR (table_name = 'writing_section' AND column_name IN
       ('content_type','image_count','table_count','image_strategy','image_requirements_json'))
   OR (table_name = 'writing_image_material' AND column_name IN ('is_confirmed','display_order'))
   OR (table_name = 'writing_table' AND column_name IN ('user_confirmed_section','is_confirmed'))
   OR (table_name = 'writing_reference' AND column_name IN
       ('language','source_type','provider','provider_record_id','citation_number','format_incomplete'))
  )
ORDER BY table_name, ordinal_position;
