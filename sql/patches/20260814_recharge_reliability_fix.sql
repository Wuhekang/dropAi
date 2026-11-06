-- Dokiai recharge reliability migration (MySQL 8+). Run before backend deploy.
ALTER TABLE recharge_order ADD COLUMN IF NOT EXISTS school_id BIGINT NOT NULL DEFAULT 0 AFTER user_id;
ALTER TABLE recharge_order ADD COLUMN IF NOT EXISTS third_party_trade_no VARCHAR(128) NULL AFTER pay_method;
ALTER TABLE recharge_order ADD COLUMN IF NOT EXISTS gateway_order_no VARCHAR(128) NULL AFTER third_party_trade_no;
ALTER TABLE recharge_order ADD COLUMN IF NOT EXISTS provider_trade_no VARCHAR(128) NULL AFTER gateway_order_no;
ALTER TABLE recharge_order ADD COLUMN IF NOT EXISTS credited_at DATETIME NULL AFTER paid_at;
ALTER TABLE recharge_order ADD COLUMN IF NOT EXISTS refund_amount DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER pay_amount;
CREATE INDEX idx_recharge_school_paid ON recharge_order (school_id, status, paid_at);
UPDATE recharge_order o JOIN user_account u ON u.id=o.user_id
SET o.school_id=COALESCE(u.school_id,0)
WHERE o.school_id=0 AND o.status IN ('paid','approved','refunded');

CREATE TABLE IF NOT EXISTS recharge_reconciliation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(64) NOT NULL,
  operator_user_id BIGINT NOT NULL,
  reason VARCHAR(255) NOT NULL,
  result VARCHAR(30) NOT NULL,
  detail VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_reconcile_order_created (order_no, created_at)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
