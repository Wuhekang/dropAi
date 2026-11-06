package com.dropai.rewrite.service.ppt.enhancement;

import com.dropai.rewrite.service.PointService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PptEnhancementBillingService {
    public static final String FEATURE_CODE = "PPT_ENHANCE";
    public static final String FEATURE_NAME = "PPT增幅美化";

    private final JdbcTemplate jdbc;
    private final PointService points;

    public PptEnhancementBillingService(JdbcTemplate jdbc, PointService points) {
        this.jdbc = jdbc;
        this.points = points;
    }

    @Transactional
    public void complete(
        String taskId,
        Long userId,
        int costPoints,
        String outputPath,
        String outputSha256,
        String planPath,
        String logPath,
        String planHash,
        int slideCount,
        long outputSize,
        String topic
    ) {
        int claimed = jdbc.update(
            "UPDATE ppt_enhancement_task SET status='FINALIZING',current_stage='正在完成质量门禁与积分结算',progress=96,updated_at=? WHERE id=? AND user_id=? AND status='RUNNING'",
            LocalDateTime.now(), taskId, userId);
        if (claimed != 1) throw new IllegalStateException("增幅美化任务已被结算或状态异常");
        points.deductCustom(userId, taskId, FEATURE_CODE, FEATURE_NAME, costPoints,
            "PPT增幅美化：" + (topic == null ? "" : topic));
        int completed = jdbc.update(
            "UPDATE ppt_enhancement_task SET status='SUCCESS',progress=100,current_stage='增幅美化完成',plan_hash=?,output_path=?,output_sha256=?,plan_path=?,log_path=?,slide_count=?,output_size=?,points_charged=TRUE,error_message=NULL,completed_at=?,updated_at=? WHERE id=? AND user_id=? AND status='FINALIZING'",
            planHash, outputPath, outputSha256, planPath, logPath, slideCount, outputSize,
            LocalDateTime.now(), LocalDateTime.now(), taskId, userId);
        if (completed != 1) throw new IllegalStateException("增幅美化任务发布失败");
    }
}
