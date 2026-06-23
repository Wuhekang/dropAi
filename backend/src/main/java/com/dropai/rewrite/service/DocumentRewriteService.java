package com.dropai.rewrite.service;

import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import com.dropai.rewrite.vo.DocumentPrecheckVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentRewriteService {

    DocumentRewriteJobVO submit(MultipartFile file, String mode, String platform);

    DocumentRewriteJobVO submit(MultipartFile file, String mode, String platform, String requestId);

    DocumentPrecheckVO precheck(MultipartFile file, String mode);

    DocumentRewriteJobVO getJob(String jobId);

    List<DocumentRewriteJobVO> listJobs();

    Resource download(String jobId);

    String downloadFileName(String jobId);
}
