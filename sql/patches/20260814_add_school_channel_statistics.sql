-- Dokiai 学校渠道及学校统计账号安全迁移（MySQL 8.0+）
-- 执行前建议备份；脚本不删除既有字段或数据。
SET NAMES utf8mb4;
START TRANSACTION;

CREATE TABLE IF NOT EXISTS school (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '不可变内部学校ID',
  school_code VARCHAR(64) NOT NULL COMMENT '专属注册链接编号，可修改',
  school_name VARCHAR(120) NOT NULL COMMENT '显示名称',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1启用/0停用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_school_code (school_code)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学校渠道';

ALTER TABLE user_account ADD COLUMN IF NOT EXISTS school_id BIGINT NOT NULL DEFAULT 0 COMMENT '内部学校ID，0为未绑定';
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS account_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '账号启用状态';
ALTER TABLE recharge_order ADD COLUMN IF NOT EXISTS refund_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '成功退款金额';
UPDATE user_account SET school_id=0 WHERE school_id IS NULL;

COMMIT;

-- 统计口径：status in ('paid','refunded') 的实际支付金额减成功退款金额；
-- 学校关联始终通过 user_account.school_id -> school.id，修改编号/名称不影响历史归属。
