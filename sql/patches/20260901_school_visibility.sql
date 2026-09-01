-- Dokiai school-management visibility flag (MySQL 8.0+).
-- Safe to re-run against an existing Dokiai schema.
SET NAMES utf8mb4;
SET @dokiai_schema = DATABASE();

SET @dokiai_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@dokiai_schema AND table_name='school' AND column_name='hidden'),
  'SELECT 1',
  'ALTER TABLE school ADD COLUMN hidden TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''仅在管理员学校和用户列表中隐藏，不影响启停、登录或业务功能'''
);
PREPARE dokiai_stmt FROM @dokiai_ddl; EXECUTE dokiai_stmt; DEALLOCATE PREPARE dokiai_stmt;

SET @dokiai_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=@dokiai_schema AND table_name='school' AND index_name='idx_school_hidden_deleted'),
  'SELECT 1',
  'CREATE INDEX idx_school_hidden_deleted ON school(hidden, deleted_at)'
);
PREPARE dokiai_stmt FROM @dokiai_ddl; EXECUTE dokiai_stmt; DEALLOCATE PREPARE dokiai_stmt;

SET @dokiai_ddl = NULL;
SET @dokiai_schema = NULL;
