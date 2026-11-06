package com.dropai.rewrite.mechanicalpaper;

import com.dropai.rewrite.service.PointService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MechanicalPaperBilling {
    private final JdbcTemplate jdbc;
    private final PointService points;
    private final ObjectMapper mapper;
    public MechanicalPaperBilling(JdbcTemplate jdbc, PointService points, ObjectMapper mapper) { this.jdbc=jdbc;this.points=points;this.mapper=mapper; }
    /** Atomic success + charge. Never hold this row lock while calling a model. */
    @Transactional public void complete(Long userId,MechanicalPaperState state) throws Exception {
        var row=jdbc.queryForMap("SELECT points_charged,user_id FROM document_job WHERE job_id=? FOR UPDATE",state.id);
        if(!userId.toString().equals(String.valueOf(row.get("user_id")))) throw new IllegalArgumentException("任务权限错误");
        boolean charged=Boolean.TRUE.equals(row.get("points_charged")) || "1".equals(String.valueOf(row.get("points_charged")));
        if(!charged) points.deductFeatureForJob(userId,state.id,PointService.MECHANICAL_PAPER,"机械说明书生成");
        // Serialize a copy; do not expose SUCCESS in memory before the transaction commits.
        var finished=mapper.valueToTree(state); ((com.fasterxml.jackson.databind.node.ObjectNode)finished).put("status","SUCCESS").put("stage","生成完成");
        jdbc.update("UPDATE document_job SET points_charged=?,status='SUCCESS',message='生成完成',paragraphs_json=?,char_count=?,updated_at=CURRENT_TIMESTAMP WHERE job_id=?",true,mapper.writeValueAsString(finished),state.wordCount,state.id);
    }
}
