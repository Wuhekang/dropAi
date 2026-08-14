package com.dropai.rewrite.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RechargeReconciliationAuditService {
    private final JdbcTemplate jdbc;
    public RechargeReconciliationAuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String orderNo, Long adminId, String reason, String result, String detail) {
        String safe = detail == null ? null : detail.substring(0, Math.min(500, detail.length()));
        jdbc.update("INSERT INTO recharge_reconciliation(order_no,operator_user_id,reason,result,detail,created_at) VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)",
                orderNo, adminId, reason, result, safe);
    }
}
