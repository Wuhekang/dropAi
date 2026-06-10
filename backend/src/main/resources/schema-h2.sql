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
  wechat_openid VARCHAR(128) NOT NULL UNIQUE,
  wechat_unionid VARCHAR(128) UNIQUE,
  nickname VARCHAR(128),
  avatar_url VARCHAR(500),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

ALTER TABLE user_account ADD COLUMN IF NOT EXISTS wechat_openid VARCHAR(128);
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS wechat_unionid VARCHAR(128);
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS nickname VARCHAR(128);
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE user_account DROP COLUMN IF EXISTS username;
ALTER TABLE user_account DROP COLUMN IF EXISTS password_hash;

CREATE TABLE IF NOT EXISTS wechat_login_state (
  state VARCHAR(64) PRIMARY KEY,
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL
);

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
