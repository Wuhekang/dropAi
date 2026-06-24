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
  phone VARCHAR(20) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  points INT NOT NULL DEFAULT 0,
  total_points INT NOT NULL DEFAULT 0,
  used_points INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE user_account ADD COLUMN IF NOT EXISTS points INT NOT NULL DEFAULT 0;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS total_points INT NOT NULL DEFAULT 0;
ALTER TABLE user_account ADD COLUMN IF NOT EXISTS used_points INT NOT NULL DEFAULT 0;
ALTER TABLE user_account ALTER COLUMN points SET DEFAULT 0;
ALTER TABLE user_account ALTER COLUMN total_points SET DEFAULT 0;
UPDATE user_account SET points = 0 WHERE points IS NULL;
UPDATE user_account SET total_points = 0 WHERE total_points IS NULL;
UPDATE user_account SET used_points = 0 WHERE used_points IS NULL;

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
  source_feature VARCHAR(50) NOT NULL DEFAULT 'REWRITE',
  mode VARCHAR(50), mode_name VARCHAR(50),
  platform VARCHAR(50), platform_name VARCHAR(50),
  status VARCHAR(30) NOT NULL,
  total_paragraphs INT DEFAULT 0,
  processed_paragraphs INT DEFAULT 0,
  rewritten_paragraphs INT DEFAULT 0,
  char_count INT DEFAULT 0,
  cost_points INT DEFAULT 0,
  points_charged TINYINT(1) NOT NULL DEFAULT 0,
  message TEXT,
  paragraphs_json LONGTEXT,
  output_file LONGBLOB,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_document_user_created (user_id, created_at)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE document_job ADD COLUMN IF NOT EXISTS char_count INT DEFAULT 0;
ALTER TABLE document_job ADD COLUMN IF NOT EXISTS cost_points INT DEFAULT 0;
ALTER TABLE document_job ADD COLUMN IF NOT EXISTS points_charged TINYINT(1) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS point_transactions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  job_id VARCHAR(64),
  feature_code VARCHAR(50) NOT NULL,
  feature_name VARCHAR(100) NOT NULL,
  points_change INT NOT NULL,
  balance_after INT NOT NULL,
  remark VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_point_tx_user_created (user_id, created_at),
  INDEX idx_point_tx_job (job_id),
  INDEX idx_point_tx_feature (feature_code)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE point_transactions ADD COLUMN IF NOT EXISTS job_id VARCHAR(64);

CREATE TABLE IF NOT EXISTS feature_pricing (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  feature_code VARCHAR(50) NOT NULL UNIQUE,
  feature_name VARCHAR(100) NOT NULL,
  cost_points INT NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO feature_pricing (feature_code, feature_name, cost_points, enabled)
SELECT 'DESIGN_GENERATE', '毕业设计成果包生成', 100, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_pricing WHERE feature_code = 'DESIGN_GENERATE');
INSERT INTO feature_pricing (feature_code, feature_name, cost_points, enabled)
SELECT 'CAD_GENERATE', 'CAD图纸生成', 50, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_pricing WHERE feature_code = 'CAD_GENERATE');
INSERT INTO feature_pricing (feature_code, feature_name, cost_points, enabled)
SELECT 'MODEL_GENERATE', '三维模型生成', 50, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_pricing WHERE feature_code = 'MODEL_GENERATE');
INSERT INTO feature_pricing (feature_code, feature_name, cost_points, enabled)
SELECT 'DOCX_GENERATE', '文档生成', 30, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_pricing WHERE feature_code = 'DOCX_GENERATE');
INSERT INTO feature_pricing (feature_code, feature_name, cost_points, enabled)
SELECT 'ZIP_EXPORT', '成果包导出', 20, 1
WHERE NOT EXISTS (SELECT 1 FROM feature_pricing WHERE feature_code = 'ZIP_EXPORT');

ALTER TABLE rewrite_record CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS workflow_node (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '节点ID',
  node_name VARCHAR(100) NOT NULL COMMENT '节点名称',
  node_type VARCHAR(50) NOT NULL COMMENT '节点类型',
  prompt_template TEXT COMMENT '提示词模板',
  input_key VARCHAR(50) COMMENT '输入键',
  output_key VARCHAR(50) COMMENT '输出键',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '执行顺序'
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE document_job MODIFY COLUMN mode VARCHAR(100);
ALTER TABLE document_job MODIFY COLUMN mode_name VARCHAR(100);
ALTER TABLE document_job MODIFY COLUMN platform VARCHAR(100);
ALTER TABLE document_job MODIFY COLUMN platform_name VARCHAR(100);

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

CREATE TABLE IF NOT EXISTS computer_generation_jobs (
  id VARCHAR(64) PRIMARY KEY COMMENT '生成任务ID',
  user_id BIGINT NOT NULL COMMENT '所属用户',
  title VARCHAR(255) NOT NULL COMMENT '项目题目',
  project_type VARCHAR(80) COMMENT '项目类型',
  tech_stack VARCHAR(120) COMMENT '技术栈',
  status VARCHAR(30) NOT NULL COMMENT '任务状态',
  progress INT DEFAULT 0 COMMENT '进度百分比',
  current_stage VARCHAR(80) COMMENT '当前阶段',
  current_file VARCHAR(500) COMMENT '当前生成文件',
  input_text LONGTEXT COMMENT '输入需求和解析文本',
  uploaded_files LONGTEXT COMMENT '上传文件名',
  output_zip_path VARCHAR(500) COMMENT 'ZIP路径',
  frontend_path VARCHAR(500) COMMENT '前端路径',
  backend_path VARCHAR(500) COMMENT '后端路径',
  sql_path VARCHAR(500) COMMENT 'SQL路径',
  paper_path VARCHAR(500) COMMENT '论文路径',
  preview_url VARCHAR(500) COMMENT '预览URL',
  error_message TEXT COMMENT '错误信息',
  points_cost INT DEFAULT 0 COMMENT '预计积分',
  points_charged TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已扣积分',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  updated_at DATETIME NOT NULL COMMENT '更新时间',
  INDEX idx_computer_job_user_created (user_id, created_at),
  INDEX idx_computer_job_status (status)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS computer_generated_files (
  id VARCHAR(64) PRIMARY KEY COMMENT '文件ID',
  job_id VARCHAR(64) NOT NULL COMMENT '任务ID',
  file_type VARCHAR(50) COMMENT '文件类型',
  file_name VARCHAR(500) COMMENT '展示文件名',
  file_path VARCHAR(500) COMMENT '服务器存储路径',
  file_size BIGINT DEFAULT 0 COMMENT '文件大小',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  INDEX idx_computer_file_job (job_id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS computer_preview_instances (
  id VARCHAR(64) PRIMARY KEY COMMENT '预览实例ID',
  job_id VARCHAR(64) NOT NULL COMMENT '任务ID',
  preview_id VARCHAR(64) NOT NULL COMMENT '随机预览ID',
  preview_url VARCHAR(500) COMMENT '预览地址',
  preview_path VARCHAR(500) COMMENT '预览目录',
  status VARCHAR(30) COMMENT '状态',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  expired_at DATETIME COMMENT '过期时间',
  INDEX idx_computer_preview_job (job_id),
  UNIQUE KEY uk_computer_preview_id (preview_id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
