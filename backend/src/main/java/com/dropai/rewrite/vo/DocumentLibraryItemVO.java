package com.dropai.rewrite.vo;

import java.time.LocalDateTime;

public class DocumentLibraryItemVO {
    private String id;
    private String projectName;
    private LocalDateTime createTime;
    private String status;
    private String packageUrl;
    private DocOutputVO doc = new DocOutputVO();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
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
