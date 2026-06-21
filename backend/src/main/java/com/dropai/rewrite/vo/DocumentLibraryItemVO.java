package com.dropai.rewrite.vo;

import java.time.LocalDateTime;

public class DocumentLibraryItemVO {
    private String id;
    private String projectName;
    private String fileName;
    private String sourceFeature;
    private String fileType;
    private LocalDateTime createTime;
    private String status;
    private String downloadUrl;
    private boolean viewable;
    private String packageUrl;
    private DocOutputVO doc = new DocOutputVO();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getSourceFeature() { return sourceFeature; }
    public void setSourceFeature(String sourceFeature) { this.sourceFeature = sourceFeature; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    public boolean isViewable() { return viewable; }
    public void setViewable(boolean viewable) { this.viewable = viewable; }
    public String getPackageUrl() { return packageUrl; }
    public void setPackageUrl(String packageUrl) { this.packageUrl = packageUrl; }
    public DocOutputVO getDoc() { return doc; }
    public void setDoc(DocOutputVO doc) { this.doc = doc; }

    public static class DocOutputVO {
        private String reduceDocUrl;
        private String aiReduceDocUrl;
        private String doubleReduceDocUrl;
        private String aiReportUrl;
        private String plagiarismReportUrl;

        public String getReduceDocUrl() { return reduceDocUrl; }
        public void setReduceDocUrl(String reduceDocUrl) { this.reduceDocUrl = reduceDocUrl; }
        public String getAiReduceDocUrl() { return aiReduceDocUrl; }
        public void setAiReduceDocUrl(String aiReduceDocUrl) { this.aiReduceDocUrl = aiReduceDocUrl; }
        public String getDoubleReduceDocUrl() { return doubleReduceDocUrl; }
        public void setDoubleReduceDocUrl(String doubleReduceDocUrl) { this.doubleReduceDocUrl = doubleReduceDocUrl; }
        public String getAiReportUrl() { return aiReportUrl; }
        public void setAiReportUrl(String aiReportUrl) { this.aiReportUrl = aiReportUrl; }
        public String getPlagiarismReportUrl() { return plagiarismReportUrl; }
        public void setPlagiarismReportUrl(String plagiarismReportUrl) { this.plagiarismReportUrl = plagiarismReportUrl; }
    }
}
