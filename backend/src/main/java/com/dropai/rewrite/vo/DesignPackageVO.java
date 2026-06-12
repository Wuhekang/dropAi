package com.dropai.rewrite.vo;

import com.dropai.rewrite.modules.model.DesignProject;
import java.util.ArrayList;
import java.util.List;

public class DesignPackageVO {
    private DesignProject project;
    private List<ArtifactVO> artifacts = new ArrayList<>();
    public DesignProject getProject() { return project; }
    public void setProject(DesignProject project) { this.project = project; }
    public List<ArtifactVO> getArtifacts() { return artifacts; }
    public void setArtifacts(List<ArtifactVO> artifacts) { this.artifacts = artifacts; }

    public static class ArtifactVO {
        private String jobId;
        private String fileName;
        private String mediaType;
        private String downloadUrl;
        public ArtifactVO() {}
        public ArtifactVO(String jobId, String fileName, String mediaType, String downloadUrl) {
            this.jobId = jobId; this.fileName = fileName; this.mediaType = mediaType; this.downloadUrl = downloadUrl;
        }
        public String getJobId() { return jobId; }
        public void setJobId(String jobId) { this.jobId = jobId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getMediaType() { return mediaType; }
        public void setMediaType(String mediaType) { this.mediaType = mediaType; }
        public String getDownloadUrl() { return downloadUrl; }
        public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    }
}
