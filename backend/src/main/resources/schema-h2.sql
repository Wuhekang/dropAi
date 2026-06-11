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
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

ALTER TABLE user_account ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS password_hash VARCHAR(100);
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
  message CLOB,
  paragraphs_json CLOB,
  output_file BLOB,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
ALTER TABLE document_job ADD COLUMN IF NOT EXISTS source_feature VARCHAR(50) DEFAULT 'REWRITE' NOT NULL;

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
