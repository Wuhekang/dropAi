CREATE TABLE IF NOT EXISTS diagram_render_task (
  id VARCHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
  diagram_type VARCHAR(32) NOT NULL, render_hash VARCHAR(64) NOT NULL,
  renderer_version VARCHAR(64) NOT NULL, status VARCHAR(20) NOT NULL,
  error_message VARCHAR(500), charged_points INT NOT NULL DEFAULT 0,
  charge_transaction_id BIGINT, refund_transaction_id BIGINT,
  created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_diagram_task_render (user_id, project_id, render_hash)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS diagram_preview (
  id VARCHAR(64) PRIMARY KEY, task_id VARCHAR(64) NOT NULL, user_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL, diagram_type VARCHAR(32) NOT NULL,
  render_hash VARCHAR(64) NOT NULL, renderer_version VARCHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL, charged_points INT NOT NULL DEFAULT 0,
  charge_transaction_id BIGINT, refund_transaction_id BIGINT, normalized_dsl MEDIUMTEXT, svg_content MEDIUMTEXT,
  created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_diagram_preview_render (user_id, project_id, render_hash)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS diagram_artifact (
  id VARCHAR(64) PRIMARY KEY, preview_id VARCHAR(64) NOT NULL, format VARCHAR(12) NOT NULL,
  status VARCHAR(20) NOT NULL, file_path VARCHAR(600), file_size BIGINT NOT NULL DEFAULT 0,
  failure_reason VARCHAR(255), created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_diagram_artifact (preview_id, format)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS diagram_preview_charge (
  id VARCHAR(64) PRIMARY KEY, task_id VARCHAR(64) NOT NULL, preview_id VARCHAR(64),
  user_id BIGINT NOT NULL, project_id BIGINT NOT NULL, render_hash VARCHAR(64) NOT NULL,
  kind VARCHAR(12) NOT NULL, points INT NOT NULL, transaction_id BIGINT,
  status VARCHAR(20) NOT NULL, related_charge_id VARCHAR(64), created_at DATETIME NOT NULL,
  UNIQUE KEY uk_diagram_charge_kind (task_id, kind)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO feature_pricing(feature_code,feature_name,cost_points,enabled)
SELECT 'DIAGRAM_PREVIEW','智能画图预览生成',10,1
WHERE NOT EXISTS (SELECT 1 FROM feature_pricing WHERE feature_code='DIAGRAM_PREVIEW');
