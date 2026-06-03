package com.dropai.rewrite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dropai.rewrite.dto.RewriteSubmitDTO;
import com.dropai.rewrite.entity.RewriteRecord;
import com.dropai.rewrite.mapper.RewriteRecordMapper;
import com.dropai.rewrite.service.RewriteRecordService;
import com.dropai.rewrite.service.WorkflowRewriteService;
import com.dropai.rewrite.utils.AiRiskAnalyzeUtil;
import com.dropai.rewrite.vo.AiAnalyzeVO;
import com.dropai.rewrite.vo.QualityCheckVO;
import com.dropai.rewrite.vo.RewriteResultVO;
import com.dropai.rewrite.vo.WorkflowStepVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class RewriteRecordServiceImpl extends ServiceImpl<RewriteRecordMapper, RewriteRecord> implements RewriteRecordService {

    private final WorkflowRewriteService workflowRewriteService;
    private final ObjectMapper objectMapper;

    public RewriteRecordServiceImpl(WorkflowRewriteService workflowRewriteService, ObjectMapper objectMapper) {
        this.workflowRewriteService = workflowRewriteService;
        this.objectMapper = objectMapper;
    }

    @Override
    public RewriteResultVO submit(RewriteSubmitDTO dto) {
        WorkflowRewriteService.WorkflowRewriteResult workflowResult =
                workflowRewriteService.execute(dto.getOriginalText(), dto.getRewriteType());
        String rewrittenText = workflowResult.getRewrittenText();
        AiAnalyzeVO analyzeVO = AiRiskAnalyzeUtil.analyze(rewrittenText);

        RewriteRecord record = new RewriteRecord();
        record.setOriginalText(dto.getOriginalText());
        record.setRewrittenText(rewrittenText);
        record.setRewriteType(dto.getRewriteType());
        record.setAiScore(analyzeVO.getScore());
        record.setAiLevel(analyzeVO.getLevel());
        record.setSuggestions(toJson(analyzeVO.getSuggestions()));
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        save(record);

        RewriteResultVO vo = toVO(record);
        vo.setAiProvider(workflowResult.getAiProvider());
        vo.setAiModel(workflowResult.getAiModel());
        vo.setQualityCheck(workflowResult.getQualityCheck());
        vo.setWorkflowSteps(workflowResult.getWorkflowSteps());
        return vo;
    }

    @Override
    public AiAnalyzeVO analyze(String originalText) {
        return AiRiskAnalyzeUtil.analyze(originalText);
    }

    @Override
    public List<RewriteResultVO> listRecords() {
        LambdaQueryWrapper<RewriteRecord> wrapper = new LambdaQueryWrapper<RewriteRecord>()
                .orderByDesc(RewriteRecord::getCreatedAt);
        return list(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    public RewriteResultVO detail(Long id) {
        RewriteRecord record = getById(id);
        return record == null ? null : toVO(record);
    }

    @Override
    public boolean deleteRecord(Long id) {
        return removeById(id);
    }

    private RewriteResultVO toVO(RewriteRecord record) {
        RewriteResultVO vo = new RewriteResultVO();
        vo.setId(record.getId());
        vo.setOriginalText(record.getOriginalText());
        vo.setRewrittenText(record.getRewrittenText());
        vo.setRewriteType(record.getRewriteType());
        vo.setAiProvider("保存记录");
        vo.setAiModel("提交时模型信息未持久化");
        vo.setAiScore(record.getAiScore() == null ? 0 : record.getAiScore());
        vo.setAiLevel(record.getAiLevel());
        vo.setSuggestions(fromJson(record.getSuggestions()));
        vo.setQualityCheck(savedQualityCheck(record));
        vo.setWorkflowSteps(savedWorkflowSteps(record));
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }

    private QualityCheckVO savedQualityCheck(RewriteRecord record) {
        String rewrittenText = record.getRewrittenText() == null ? "" : record.getRewrittenText();
        QualityCheckVO vo = new QualityCheckVO();
        vo.setMeaningPreserved(rewrittenText.length() >= Math.max(1, record.getOriginalText().length() * 0.35));
        vo.setAcademicTone(!rewrittenText.contains("我觉得") && !rewrittenText.contains("随便"));
        vo.setTemplateReduced(!rewrittenText.contains("首先")
                && !rewrittenText.contains("其次")
                && !rewrittenText.contains("最后")
                && !rewrittenText.contains("综上所述"));
        vo.setFluent(!rewrittenText.isBlank() && !rewrittenText.contains("  "));

        List<String> issues = new ArrayList<>();
        if (!vo.isMeaningPreserved()) {
            issues.add("改写后文本过短，可能存在原意缺失");
        }
        if (!vo.isAcademicTone()) {
            issues.add("仍存在口语化表达");
        }
        if (!vo.isTemplateReduced()) {
            issues.add("仍包含明显模板化连接词");
        }
        if (!vo.isFluent()) {
            issues.add("文本流畅度需要继续检查");
        }
        vo.setIssues(issues);
        return vo;
    }

    private List<WorkflowStepVO> savedWorkflowSteps(RewriteRecord record) {
        return Arrays.asList(
                new WorkflowStepVO("TEXT_PREPROCESS", "文本预处理", "记录提交时已完成文本清理"),
                new WorkflowStepVO("AI_TRACE_ANALYZE", "AI痕迹分析 Skill",
                        "保存评分：" + record.getAiScore() + "，等级：" + record.getAiLevel()),
                new WorkflowStepVO("REWRITE_PLAN", "改写策略规划 Skill", "按“" + record.getRewriteType() + "”制定受控改写策略"),
                new WorkflowStepVO("SENTENCE_REWRITE", "分句改写 Skill", "提交时已调用模型执行分句改写"),
                new WorkflowStepVO("HUMAN_EXPRESSION_ADJUST", "人工化表达调整 Skill", "提交时已完成自然化表达调整和模板词清理"),
                new WorkflowStepVO("QUALITY_CHECK", "质量检查 Skill", "基于保存结果生成当前质量摘要")
        );
    }

    private String toJson(List<String> suggestions) {
        try {
            return objectMapper.writeValueAsString(suggestions);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> fromJson(String suggestions) {
        if (suggestions == null || suggestions.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(suggestions, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }
}
