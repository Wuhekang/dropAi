package com.dropai.rewrite.modules.stepExportEngine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CadWorkerStartupHealthLogger implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(CadWorkerStartupHealthLogger.class);

    private final CadWorkerHealthService healthService;

    public CadWorkerStartupHealthLogger(CadWorkerHealthService healthService) {
        this.healthService = healthService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, Object> health = healthService.health();
        Object status = health.getOrDefault("status", "DOWN");
        Object errorCode = health.getOrDefault("errorCode", "");
        Object cadqueryVersion = health.getOrDefault("cadqueryVersion", "");
        log.info("CAD_WORKER_HEALTH={} ERROR_CODE={} CADQUERY_VERSION={}", status, errorCode, cadqueryVersion);
    }
}
