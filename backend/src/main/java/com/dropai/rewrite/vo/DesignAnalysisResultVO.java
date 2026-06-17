package com.dropai.rewrite.vo;

import com.dropai.rewrite.modules.documentParser.DocumentParser;
import com.dropai.rewrite.modules.model.DesignProject;

import java.util.List;

public class DesignAnalysisResultVO {
    private String projectId;
    private String title;
    private String equipmentName;
    private String designType;
    private String projectCategory;
    private List<String> mainFunctions;
    private List<String> mainStructures;
    private List<DesignProject.Parameter> parameters;
    private List<DocumentStatusVO> documents;
    private DesignProject project;
    private String status;
    private String message;

    public static DesignAnalysisResultVO of(DesignProject project, List<DocumentParser.ParsedDocument> documents) {
        DesignAnalysisResultVO vo = new DesignAnalysisResultVO();
        vo.project = project;
        vo.projectId = project.getProjectId();
        vo.title = project.getProjectTitle();
        vo.equipmentName = project.getEquipmentName();
        vo.designType = project.getDesignType();
        vo.projectCategory = project.getProjectCategory();
        vo.mainFunctions = project.getMainFunctions();
        vo.mainStructures = project.getMainStructures();
        vo.parameters = project.allParameters();
        vo.documents = documents.stream().map(DocumentStatusVO::of).toList();
        boolean hasTitle = vo.title != null && !vo.title.isBlank();
        boolean hasReadable = documents.stream().anyMatch(DocumentParser.ParsedDocument::textReadable);
        vo.status = hasTitle && hasReadable ? "success" : "failed";
        vo.message = vo.status.equals("success") ? "已识别设计目标" : "未能从任务书中识别，请手动填写或重新上传资料。";
        return vo;
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getEquipmentName() { return equipmentName; }
    public void setEquipmentName(String equipmentName) { this.equipmentName = equipmentName; }
    public String getDesignType() { return designType; }
    public void setDesignType(String designType) { this.designType = designType; }
    public String getProjectCategory() { return projectCategory; }
    public void setProjectCategory(String projectCategory) { this.projectCategory = projectCategory; }
    public List<String> getMainFunctions() { return mainFunctions; }
    public void setMainFunctions(List<String> mainFunctions) { this.mainFunctions = mainFunctions; }
    public List<String> getMainStructures() { return mainStructures; }
    public void setMainStructures(List<String> mainStructures) { this.mainStructures = mainStructures; }
    public List<DesignProject.Parameter> getParameters() { return parameters; }
    public void setParameters(List<DesignProject.Parameter> parameters) { this.parameters = parameters; }
    public List<DocumentStatusVO> getDocuments() { return documents; }
    public void setDocuments(List<DocumentStatusVO> documents) { this.documents = documents; }
    public DesignProject getProject() { return project; }
    public void setProject(DesignProject project) { this.project = project; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public static class DocumentStatusVO {
        private String fileName;
        private String type;
        private String status;
        private boolean textReadable;
        private String failureReason;

        public static DocumentStatusVO of(DocumentParser.ParsedDocument document) {
            DocumentStatusVO vo = new DocumentStatusVO();
            vo.fileName = document.fileName();
            vo.type = document.type();
            vo.status = document.status();
            vo.textReadable = document.textReadable();
            vo.failureReason = document.failureReason();
            return vo;
        }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public boolean isTextReadable() { return textReadable; }
        public void setTextReadable(boolean textReadable) { this.textReadable = textReadable; }
        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    }
}
