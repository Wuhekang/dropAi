CREATE TABLE IF NOT EXISTS rewrite_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  original_text CLOB NOT NULL,
  rewritten_text CLOB,
  rewrite_type VARCHAR(50) NOT NULL,
  ai_score INT DEFAULT 0,
  ai_level VARCHAR(20),
  suggestions CLOB,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS workflow_node (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  node_name VARCHAR(100) NOT NULL,
  node_type VARCHAR(50) NOT NULL,
  prompt_template CLOB,
  input_key VARCHAR(50),
  output_key VARCHAR(50),
  sort_order INT NOT NULL DEFAULT 0
);
