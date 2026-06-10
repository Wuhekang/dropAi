SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS rewrite_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
  user_id BIGINT NOT NULL COMMENT '所属账号',
  original_text TEXT NOT NULL COMMENT '原文内容',
  rewritten_text TEXT COMMENT '改写后内容',
  rewrite_type VARCHAR(50) NOT NULL COMMENT '改写类型',
  ai_score INT DEFAULT 0 COMMENT 'AI痕迹风险分数',
  ai_level VARCHAR(20) COMMENT 'AI痕迹等级',
  suggestions TEXT COMMENT '优化建议JSON',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_account (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(32) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  created_at DATETIME NOT NULL
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_session (
  token VARCHAR(64) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_session_user (user_id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS document_job (
  job_id VARCHAR(64) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  mode VARCHAR(50), mode_name VARCHAR(50),
  platform VARCHAR(50), platform_name VARCHAR(50),
  status VARCHAR(30) NOT NULL,
  total_paragraphs INT DEFAULT 0,
  processed_paragraphs INT DEFAULT 0,
  rewritten_paragraphs INT DEFAULT 0,
  message TEXT,
  paragraphs_json LONGTEXT,
  output_file LONGBLOB,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_document_user_created (user_id, created_at)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE rewrite_record CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE rewrite_record ADD COLUMN IF NOT EXISTS user_id BIGINT NULL COMMENT '所属账号';

CREATE TABLE IF NOT EXISTS workflow_node (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '节点ID',
  node_name VARCHAR(100) NOT NULL COMMENT '节点名称',
  node_type VARCHAR(50) NOT NULL COMMENT '节点类型',
  prompt_template TEXT COMMENT '提示词模板',
  input_key VARCHAR(50) COMMENT '输入键',
  output_key VARCHAR(50) COMMENT '输出键',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '执行顺序'
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE workflow_node CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO workflow_node (node_name, node_type, prompt_template, input_key, output_key, sort_order)
SELECT 'AI痕迹分析 Skill', 'AI_TRACE_ANALYZE', '分析模板词、句长、连接词密度和段落结构', 'originalText', 'aiRisk', 10
WHERE NOT EXISTS (SELECT 1 FROM workflow_node WHERE node_type = 'AI_TRACE_ANALYZE');

INSERT INTO workflow_node (node_name, node_type, prompt_template, input_key, output_key, sort_order)
SELECT '改写策略规划 Skill', 'REWRITE_PLAN', '先制定保留原意、调整语序、减少连接词的改写策略', 'aiRisk', 'rewritePlan', 20
WHERE NOT EXISTS (SELECT 1 FROM workflow_node WHERE node_type = 'REWRITE_PLAN');

INSERT INTO workflow_node (node_name, node_type, prompt_template, input_key, output_key, sort_order)
SELECT '分句改写 Skill', 'SENTENCE_REWRITE', '按句改写，不新增虚假案例，不连续三句使用相同结构', 'rewritePlan', 'sentenceRewrite', 30
WHERE NOT EXISTS (SELECT 1 FROM workflow_node WHERE node_type = 'SENTENCE_REWRITE');

INSERT INTO workflow_node (node_name, node_type, prompt_template, input_key, output_key, sort_order)
SELECT '学术风格润色 Skill', 'ACADEMIC_POLISH', '统一论文语气，避免过度扩写和口语化表达', 'sentenceRewrite', 'academicText', 40
WHERE NOT EXISTS (SELECT 1 FROM workflow_node WHERE node_type = 'ACADEMIC_POLISH');

INSERT INTO workflow_node (node_name, node_type, prompt_template, input_key, output_key, sort_order)
SELECT '质量检查 Skill', 'QUALITY_CHECK', '检查原意偏离、AI味词汇、语句通顺和重复风险', 'academicText', 'qualityCheck', 50
WHERE NOT EXISTS (SELECT 1 FROM workflow_node WHERE node_type = 'QUALITY_CHECK');
