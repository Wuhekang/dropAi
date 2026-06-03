package com.dropai.rewrite.service;

import com.dropai.rewrite.vo.QualityCheckVO;
import com.dropai.rewrite.vo.WorkflowStepVO;

import java.util.List;

public interface WorkflowRewriteService {

    WorkflowRewriteResult execute(String originalText, String rewriteType);

    class WorkflowRewriteResult {

        private String rewrittenText;
        private String aiProvider;
        private String aiModel;
        private QualityCheckVO qualityCheck;
        private List<WorkflowStepVO> workflowSteps;

        public String getRewrittenText() {
            return rewrittenText;
        }

        public void setRewrittenText(String rewrittenText) {
            this.rewrittenText = rewrittenText;
        }

        public String getAiProvider() {
            return aiProvider;
        }

        public void setAiProvider(String aiProvider) {
            this.aiProvider = aiProvider;
        }

        public String getAiModel() {
            return aiModel;
        }

        public void setAiModel(String aiModel) {
            this.aiModel = aiModel;
        }

        public QualityCheckVO getQualityCheck() {
            return qualityCheck;
        }

        public void setQualityCheck(QualityCheckVO qualityCheck) {
            this.qualityCheck = qualityCheck;
        }

        public List<WorkflowStepVO> getWorkflowSteps() {
            return workflowSteps;
        }

        public void setWorkflowSteps(List<WorkflowStepVO> workflowSteps) {
            this.workflowSteps = workflowSteps;
        }
    }
}
