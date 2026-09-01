-- Dokiai school account controls and school-specific recharge pricing (MySQL 8.0+)
-- Run against an existing Dokiai schema. MySQL DDL commits implicitly, so every
-- step is guarded through information_schema and can be safely re-run.
SET NAMES utf8mb4;
SET @dokiai_schema = DATABASE();
SET @dokiai_had_student_min_price = EXISTS(
  SELECT 1 FROM information_schema.columns
  WHERE table_schema=@dokiai_schema AND table_name='school'
    AND column_name='student_recharge_min_price_per10'
);

SET @dokiai_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@dokiai_schema AND table_name='user_account' AND column_name='deleted_at'),
  'SELECT 1',
  'ALTER TABLE user_account ADD COLUMN deleted_at DATETIME NULL COMMENT ''伪删除时间'''
);
PREPARE dokiai_stmt FROM @dokiai_ddl; EXECUTE dokiai_stmt; DEALLOCATE PREPARE dokiai_stmt;

SET @dokiai_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@dokiai_schema AND table_name='user_account' AND column_name='deleted_by'),
  'SELECT 1',
  'ALTER TABLE user_account ADD COLUMN deleted_by BIGINT NULL COMMENT ''执行删除的账号ID'''
);
PREPARE dokiai_stmt FROM @dokiai_ddl; EXECUTE dokiai_stmt; DEALLOCATE PREPARE dokiai_stmt;

SET @dokiai_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@dokiai_schema AND table_name='user_account' AND column_name='delete_reason'),
  'SELECT 1',
  'ALTER TABLE user_account ADD COLUMN delete_reason VARCHAR(255) NULL COMMENT ''删除原因'''
);
PREPARE dokiai_stmt FROM @dokiai_ddl; EXECUTE dokiai_stmt; DEALLOCATE PREPARE dokiai_stmt;

SET @dokiai_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@dokiai_schema AND table_name='school' AND column_name='recharge_price_per10'),
  'SELECT 1',
  'ALTER TABLE school ADD COLUMN recharge_price_per10 DECIMAL(10,2) NOT NULL DEFAULT 0.30 COMMENT ''学校账户兑换10积分所需金额'''
);
PREPARE dokiai_stmt FROM @dokiai_ddl; EXECUTE dokiai_stmt; DEALLOCATE PREPARE dokiai_stmt;

SET @dokiai_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@dokiai_schema AND table_name='school' AND column_name='student_recharge_price_per10'),
  'SELECT 1',
  'ALTER TABLE school ADD COLUMN student_recharge_price_per10 DECIMAL(10,2) NOT NULL DEFAULT 2.00 COMMENT ''下级注册账号兑换10积分所需金额'''
);
PREPARE dokiai_stmt FROM @dokiai_ddl; EXECUTE dokiai_stmt; DEALLOCATE PREPARE dokiai_stmt;

SET @dokiai_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@dokiai_schema AND table_name='school' AND column_name='student_recharge_min_price_per10'),
  'SELECT 1',
  'ALTER TABLE school ADD COLUMN student_recharge_min_price_per10 DECIMAL(10,2) NOT NULL DEFAULT 1.00 COMMENT ''管理员设置的下级账号每10积分最低限价'''
);
PREPARE dokiai_stmt FROM @dokiai_ddl; EXECUTE dokiai_stmt; DEALLOCATE PREPARE dokiai_stmt;

UPDATE school
SET student_recharge_price_per10 = COALESCE(student_recharge_price_per10, 2.00),
    student_recharge_min_price_per10 = COALESCE(student_recharge_min_price_per10, 1.00);

SET @dokiai_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@dokiai_schema AND table_name='school' AND column_name='student_recharge_price_per10' AND column_default='2.00'),
  'SELECT 1',
  'ALTER TABLE school MODIFY COLUMN student_recharge_price_per10 DECIMAL(10,2) NOT NULL DEFAULT 2.00 COMMENT ''下级注册账号兑换10积分所需金额'''
);
PREPARE dokiai_stmt FROM @dokiai_ddl; EXECUTE dokiai_stmt; DEALLOCATE PREPARE dokiai_stmt;

SET @dokiai_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@dokiai_schema AND table_name='school' AND column_name='student_recharge_min_price_per10' AND column_default='1.00'),
  'SELECT 1',
  'ALTER TABLE school MODIFY COLUMN student_recharge_min_price_per10 DECIMAL(10,2) NOT NULL DEFAULT 1.00 COMMENT ''管理员设置的下级账号每10积分最低限价'''
);
PREPARE dokiai_stmt FROM @dokiai_ddl; EXECUTE dokiai_stmt; DEALLOCATE PREPARE dokiai_stmt;

SET @dokiai_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@dokiai_schema AND table_name='school' AND column_name='deleted_at'),
  'SELECT 1',
  'ALTER TABLE school ADD COLUMN deleted_at DATETIME NULL COMMENT ''伪删除时间'''
);
PREPARE dokiai_stmt FROM @dokiai_ddl; EXECUTE dokiai_stmt; DEALLOCATE PREPARE dokiai_stmt;

SET @dokiai_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@dokiai_schema AND table_name='school' AND column_name='deleted_by'),
  'SELECT 1',
  'ALTER TABLE school ADD COLUMN deleted_by BIGINT NULL COMMENT ''执行删除的账号ID'''
);
PREPARE dokiai_stmt FROM @dokiai_ddl; EXECUTE dokiai_stmt; DEALLOCATE PREPARE dokiai_stmt;

SET @dokiai_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@dokiai_schema AND table_name='school' AND column_name='delete_reason'),
  'SELECT 1',
  'ALTER TABLE school ADD COLUMN delete_reason VARCHAR(255) NULL COMMENT ''删除原因'''
);
PREPARE dokiai_stmt FROM @dokiai_ddl; EXECUTE dokiai_stmt; DEALLOCATE PREPARE dokiai_stmt;

SET @dokiai_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@dokiai_schema AND table_name='recharge_order' AND column_name='recharge_price_per10'),
  'SELECT 1',
  'ALTER TABLE recharge_order ADD COLUMN recharge_price_per10 DECIMAL(10,2) NULL COMMENT ''下单时每10积分价格快照'''
);
PREPARE dokiai_stmt FROM @dokiai_ddl; EXECUTE dokiai_stmt; DEALLOCATE PREPARE dokiai_stmt;

UPDATE school
SET recharge_price_per10 = 0.30
WHERE recharge_price_per10 IS NULL OR recharge_price_per10 < 0.30;

UPDATE school
SET student_recharge_min_price_per10 = CASE
  WHEN student_recharge_min_price_per10 IS NULL THEN 1.00
  WHEN student_recharge_min_price_per10 < 0.30 THEN 0.30
  ELSE student_recharge_min_price_per10
END;

SET @dokiai_ddl = IF(
  @dokiai_had_student_min_price = 0,
  'UPDATE school SET student_recharge_price_per10=GREATEST(COALESCE(student_recharge_price_per10,2.00),2.00,COALESCE(recharge_price_per10,0.30),student_recharge_min_price_per10)',
  'UPDATE school SET student_recharge_price_per10=CASE WHEN student_recharge_price_per10 IS NULL OR student_recharge_price_per10<GREATEST(COALESCE(recharge_price_per10,0.30),student_recharge_min_price_per10,0.30) THEN GREATEST(2.00,COALESCE(recharge_price_per10,0.30),student_recharge_min_price_per10,0.30) ELSE student_recharge_price_per10 END'
);
PREPARE dokiai_stmt FROM @dokiai_ddl; EXECUTE dokiai_stmt; DEALLOCATE PREPARE dokiai_stmt;

SET @dokiai_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=@dokiai_schema AND table_name='user_account' AND index_name='idx_user_school_role_deleted'),
  'SELECT 1',
  'CREATE INDEX idx_user_school_role_deleted ON user_account(school_id, role, deleted_at)'
);
PREPARE dokiai_stmt FROM @dokiai_ddl; EXECUTE dokiai_stmt; DEALLOCATE PREPARE dokiai_stmt;

SET @dokiai_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=@dokiai_schema AND table_name='school' AND index_name='idx_school_deleted'),
  'SELECT 1',
  'CREATE INDEX idx_school_deleted ON school(deleted_at)'
);
PREPARE dokiai_stmt FROM @dokiai_ddl; EXECUTE dokiai_stmt; DEALLOCATE PREPARE dokiai_stmt;

SET @dokiai_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=@dokiai_schema AND table_name='recharge_order' AND index_name='idx_recharge_school'),
  'SELECT 1',
  'CREATE INDEX idx_recharge_school ON recharge_order(school_id)'
);
PREPARE dokiai_stmt FROM @dokiai_ddl; EXECUTE dokiai_stmt; DEALLOCATE PREPARE dokiai_stmt;

SET @dokiai_ddl = NULL;
SET @dokiai_had_student_min_price = NULL;
SET @dokiai_schema = NULL;
