package com.dropai.rewrite.vo;

import com.dropai.rewrite.modules.model.DesignProject;
import java.util.ArrayList;
import java.util.List;

public class DesignPackageVO {
    private DesignProject project;
    private String status;
    private String message;
    private List<ArtifactVO> artifacts = new ArrayList<>();
    public DesignProject getProject() { return project; }
    public void setProject(DesignProject project) { this.project = project; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<ArtifactVO> getArtifacts() { return artifacts; }
    public void setArtifacts(List<ArtifactVO> artifacts) { this.artifacts = artifacts; }

    public static class ArtifactVO {
        private String jobId;
        private String type;
        private String name;
        private String path;
        private String fileName;
        private String mediaType;
        private String downloadUrl;
        private String status;
        private long size;
        private String failureReason;
        public ArtifactVO() {}
        public ArtifactVO(String jobId, String fileName, String mediaType, String downloadUrl, String status, long size, String failureReason) {
            this.jobId = jobId; this.type = extension(fileName); this.name = fileName;
            this.path = jobId == null ? null : "/documents/" + jobId; this.fileName = fileName; this.mediaType = mediaType;
            this.downloadUrl = downloadUrl; this.status = status; this.size = size; this.failureReason = failureReason;
        }
        public String getJobId() { return jobId; }
        public void setJobId(String jobId) { this.jobId = jobId; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getMediaType() { return mediaType; }
        public void setMediaType(String mediaType) { this.mediaType = mediaType; }
        public String getDownloadUrl() { return downloadUrl; }
        public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
        private static String extension(String fileName) {
            int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
            return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
        }
    }
}
