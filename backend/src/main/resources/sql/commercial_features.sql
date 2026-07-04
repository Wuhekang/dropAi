-- DropAI commercial feature schema sync
-- Scope: only the new recharge/points-log/notice tables and notice-read columns.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS recharge_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL COMMENT '用户ID',
  order_no VARCHAR(64) NOT NULL COMMENT '订单号',
  amount DECIMAL(10,2) NOT NULL COMMENT '支付金额',
  points INT NOT NULL COMMENT '到账积分',
  status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/paid/failed',
  pay_method VARCHAR(30) DEFAULT 'alipay_mock' COMMENT '支付方式',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  paid_at DATETIME NULL COMMENT '支付时间',
  UNIQUE KEY uk_recharge_order_no (order_no),
  KEY idx_recharge_user_created (user_id, created_at),
  KEY idx_recharge_status (status)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分充值订单';

CREATE TABLE IF NOT EXISTS user_points_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL COMMENT '用户ID',
  change_amount INT NOT NULL COMMENT '积分变化量',
  before_points INT NOT NULL COMMENT '变动前积分',
  after_points INT NOT NULL COMMENT '变动后积分',
  reason VARCHAR(50) NOT NULL COMMENT 'recharge/consume/refund或功能编码',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY idx_user_points_log_user_created (user_id, created_at),
  KEY idx_user_points_log_reason (reason)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户积分流水日志';

CREATE TABLE IF NOT EXISTS system_notice (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(120) NOT NULL COMMENT '公告标题',
  content TEXT NOT NULL COMMENT 'Markdown公告内容',
  status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'active/inactive',
  is_popup TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否登录弹窗',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_system_notice_status_popup (status, is_popup, created_at)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统公告';

ALTER TABLE user_account
  ADD COLUMN IF NOT EXISTS last_notice_time DATETIME NULL COMMENT '最后公告阅读时间';

ALTER TABLE user_account
  ADD COLUMN IF NOT EXISTS notice_read_id BIGINT NULL COMMENT '已读公告ID';

INSERT INTO system_notice (title, content, status, is_popup, created_at, updated_at)
SELECT
  '系统更新公告',
  '# DropAI 系统更新公告

- 积分充值功能已上线
- 生成任务将按积分校验执行

---

请确保账户积分充足后再使用生成功能。',
  'active',
  1,
  NOW(),
  NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM system_notice WHERE status = 'active' AND is_popup = 1
);
