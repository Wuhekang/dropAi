package com.dropai.rewrite.external;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class XuejieExternalSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public XuejieExternalSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS xuejie_external_job_state (
                  job_id VARCHAR(64) PRIMARY KEY,
                  user_id BIGINT NOT NULL,
                  original_name VARCHAR(255) NOT NULL,
                  platform VARCHAR(50) NOT NULL,
                  mode VARCHAR(30) NOT NULL,
                  feature_code VARCHAR(80) NOT NULL,
                  feature_name VARCHAR(100) NOT NULL,
                  cost_points INT NOT NULL DEFAULT 0,
                  stage VARCHAR(30) NOT NULL,
                  remote_task_id VARCHAR(128),
                  remote_status VARCHAR(50),
                  refund_state VARCHAR(20) NOT NULL DEFAULT 'NONE',
                  created_at TIMESTAMP NOT NULL,
                  updated_at TIMESTAMP NOT NULL
                )
                """);
    }
}
