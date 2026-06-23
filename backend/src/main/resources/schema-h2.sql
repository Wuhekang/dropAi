CREATE TABLE IF NOT EXISTS rewrite_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  original_text CLOB NOT NULL,
  rewritten_text CLOB,
  rewrite_type VARCHAR(50) NOT NULL,
  ai_score INT DEFAULT 0,
  ai_level VARCHAR(20),
  suggestions CLOB,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_account (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  phone VARCHAR(20) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(20) DEFAULT 'USER' NOT NULL,
  points INT DEFAULT 0 NOT NULL,
  total_points INT DEFAULT 0 NOT NULL,
  used_points INT DEFAULT 0 NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

ALTER TABLE user_account ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS password_hash VARCHAR(100);
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT 'USER' NOT NULL;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS points INT DEFAULT 0 NOT NULL;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS total_points INT DEFAULT 0 NOT NULL;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS used_points INT DEFAULT 0 NOT NULL;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE user_account DROP COLUMN IF EXISTS wechat_openid;
ALTER TABLE user_account DROP COLUMN IF EXISTS wechat_unionid;
ALTER TABLE user_account DROP COLUMN IF EXISTS nickname;
ALTER TABLE user_account DROP COLUMN IF EXISTS avatar_url;
DROP TABLE IF EXISTS wechat_login_state;

CREATE TABLE IF NOT EXISTS user_session (
  token VARCHAR(64) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS document_job (
  job_id VARCHAR(64) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  source_feature VARCHAR(50) DEFAULT 'REWRITE' NOT NULL,
  mode VARCHAR(50), mode_name VARCHAR(50),
  platform VARCHAR(50), platform_name VARCHAR(50),
  status VARCHAR(30) NOT NULL,
  total_paragraphs INT DEFAULT 0,
  processed_paragraphs INT DEFAULT 0,
  rewritten_paragraphs INT DEFAULT 0,
  char_count INT DEFAULT 0,
  cost_points INT DEFAULT 0,
  points_charged BOOLEAN DEFAULT FALSE NOT NULL,
  message CLOB,
  paragraphs_json CLOB,
  output_file BLOB,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
ALTER TABLE document_job ADD COLUMN IF NOT EXISTS source_feature VARCHAR(50) DEFAULT 'REWRITE' NOT NULL;
ALTER TABLE document_job ADD COLUMN IF NOT EXISTS char_count INT DEFAULT 0;
ALTER TABLE document_job ADD COLUMN IF NOT EXISTS cost_points INT DEFAULT 0;
ALTER TABLE document_job ADD COLUMN IF NOT EXISTS points_charged BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE document_job ALTER COLUMN mode VARCHAR(100);
ALTER TABLE document_job ALTER COLUMN mode_name VARCHAR(100);
ALTER TABLE document_job ALTER COLUMN platform VARCHAR(100);
ALTER TABLE document_job ALTER COLUMN platform_name VARCHAR(100);

CREATE TABLE IF NOT EXISTS point_transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  job_id VARCHAR(64),
  feature_code VARCHAR(50) NOT NULL,
  feature_name VARCHAR(100) NOT NULL,
  points_change INT NOT NULL,
  balance_after INT NOT NULL,
  remark VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
ALTER TABLE point_transactions ADD COLUMN IF NOT EXISTS job_id VARCHAR(64);

CREATE TABLE IF NOT EXISTS feature_pricing (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  feature_code VARCHAR(50) NOT NULL UNIQUE,
  feature_name VARCHAR(100) NOT NULL,
  cost_points INT NOT NULL,
  enabled BOOLEAN DEFAULT TRUE NOT NULL
);

MERGE INTO feature_pricing (feature_code, feature_name, cost_points, enabled) KEY(feature_code)
VALUES ('DESIGN_GENERATE', '毕业设计成果包生成', 100, TRUE);
MERGE INTO feature_pricing (feature_code, feature_name, cost_points, enabled) KEY(feature_code)
VALUES ('CAD_GENERATE', 'CAD图纸生成', 50, TRUE);
MERGE INTO feature_pricing (feature_code, feature_name, cost_points, enabled) KEY(feature_code)
VALUES ('MODEL_GENERATE', '三维模型生成', 50, TRUE);
MERGE INTO feature_pricing (feature_code, feature_name, cost_points, enabled) KEY(feature_code)
VALUES ('DOCX_GENERATE', '文档生成', 30, TRUE);
MERGE INTO feature_pricing (feature_code, feature_name, cost_points, enabled) KEY(feature_code)
VALUES ('ZIP_EXPORT', '成果包导出', 20, TRUE);

CREATE TABLE IF NOT EXISTS workflow_node (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  node_name VARCHAR(100) NOT NULL,
  node_type VARCHAR(50) NOT NULL,
  prompt_template CLOB,
  input_key VARCHAR(50),
  output_key VARCHAR(50),
  sort_order INT NOT NULL DEFAULT 0
);

ALTER TABLE rewrite_record ADD COLUMN IF NOT EXISTS user_id BIGINT;
