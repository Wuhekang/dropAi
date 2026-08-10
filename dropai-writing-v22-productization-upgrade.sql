-- DropAI 纯文字稿 V2.2：图片任务、视觉识别与生成前确认门禁
-- 适用：MySQL 5.7 / 8.x（不依赖 ADD COLUMN IF NOT EXISTS）
-- 部署顺序：先备份数据库，再执行本文件，然后部署后端 JAR，最后部署前端 dist。
-- 充值规则不需要修改数据库表。

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS writing_image_task (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    chapter_id VARCHAR(64) NOT NULL,
    section_id VARCHAR(64) NOT NULL,
    requirement_name VARCHAR(255) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    material_id VARCHAR(64),
    message TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_wit_project_section (project_id, section_id),
    INDEX idx_wit_status (project_id, status)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS dropai_add_column;
DELIMITER $$
CREATE PROCEDURE dropai_add_column(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_definition TEXT
)
BEGIN
    DECLARE v_column_count INT DEFAULT 0;
    SELECT COUNT(*) INTO v_column_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND COLUMN_NAME = p_column_name;

    IF v_column_count = 0 THEN
        SET @dropai_ddl = CONCAT(
            'ALTER TABLE `', REPLACE(p_table_name, '`', '``'),
            '` ADD COLUMN `', REPLACE(p_column_name, '`', '``'),
            '` ', p_column_definition
        );
        PREPARE dropai_stmt FROM @dropai_ddl;
        EXECUTE dropai_stmt;
        DEALLOCATE PREPARE dropai_stmt;
    END IF;
END$$
DELIMITER ;

CALL dropai_add_column('writing_image_material', 'is_confirmed', 'TINYINT(1) NOT NULL DEFAULT 0');
CALL dropai_add_column('writing_image_material', 'display_order', 'INT NOT NULL DEFAULT 0');
CALL dropai_add_column('writing_image_material', 'vision_description', 'TEXT');
CALL dropai_add_column('writing_image_material', 'vision_confidence', 'DECIMAL(5,4) NOT NULL DEFAULT 0');

CALL dropai_add_column('writing_project', 'document_mode', 'VARCHAR(40) NOT NULL DEFAULT ''general''');
CALL dropai_add_column('writing_project', 'project_location', 'VARCHAR(500)');
CALL dropai_add_column('writing_project', 'reference_mode', 'VARCHAR(20) NOT NULL DEFAULT ''AI''');

CALL dropai_add_column('writing_chapter', 'chapter_type', 'VARCHAR(40) NOT NULL DEFAULT ''content''');

CALL dropai_add_column('writing_section', 'content_type', 'VARCHAR(40) NOT NULL DEFAULT ''general''');
CALL dropai_add_column('writing_section', 'image_count', 'INT NOT NULL DEFAULT 0');
CALL dropai_add_column('writing_section', 'table_count', 'INT NOT NULL DEFAULT 0');
CALL dropai_add_column('writing_section', 'image_strategy', 'VARCHAR(40) NOT NULL DEFAULT ''none''');
CALL dropai_add_column('writing_section', 'image_requirements_json', 'LONGTEXT');

CALL dropai_add_column('writing_table', 'user_confirmed_section', 'VARCHAR(64)');
CALL dropai_add_column('writing_table', 'is_confirmed', 'TINYINT(1) NOT NULL DEFAULT 0');

DROP PROCEDURE IF EXISTS dropai_add_column;

UPDATE writing_image_material
SET source_type = 'USER_UPLOAD'
WHERE UPPER(source_type) = 'UPLOAD';

UPDATE writing_chapter
SET chapter_type = 'reference'
WHERE LOWER(title) = 'references' OR title LIKE '%参考文献%';

UPDATE writing_chapter
SET chapter_type = 'acknowledgement'
WHERE LOWER(title) IN ('acknowledgement', 'acknowledgments') OR title LIKE '%致谢%';

UPDATE writing_chapter
SET chapter_type = 'conclusion'
WHERE chapter_type = 'content'
  AND (title LIKE '%结论%' OR title LIKE '%展望%' OR title LIKE '%总结%');
