package com.dropai.rewrite.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.service.DocumentRewriteService;
import com.dropai.rewrite.vo.DocumentLibraryItemVO;
import com.dropai.rewrite.vo.DocumentLibraryPageVO;
import com.dropai.rewrite.vo.Result;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentLibraryController {
    private final DocumentRewriteService documentService;
    private final DocumentJobMapper documentJobMapper;

    public DocumentLibraryController(DocumentRewriteService documentService, DocumentJobMapper documentJobMapper) {
        this.documentService = documentService;
        this.documentJobMapper = documentJobMapper;
    }

    @GetMapping
    public Result<DocumentLibraryPageVO> documents(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize
    ) {
        Long userId = AuthContext.requireUserId();
        int safePageNum = Math.max(1, pageNum);
        int safePageSize = Math.max(1, Math.min(pageSize, 10));
        long offset = (long) (safePageNum - 1) * safePageSize;
        LambdaQueryWrapper<DocumentJobRecord> query = libraryQuery(userId);
        long total = documentJobMapper.selectCount(query);
        List<DocumentLibraryItemVO> list = documentJobMapper.selectList(libraryQuery(userId)
                        .orderByDesc(DocumentJobRecord::getCreatedAt)
                        .last("LIMIT " + offset + ", " + safePageSize))
                .stream()
                .map(this::toLibraryItem)
                .toList();
        return Result.success(new DocumentLibraryPageVO(list, total, safePageNum, safePageSize));
    }

    @GetMapping("/{jobId}/download")
    public ResponseEntity<Resource> download(@PathVariable String jobId) {
        String fileName = URLEncoder.encode(documentService.downloadFileName(jobId), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(documentService.download(jobId));
    }

    private LambdaQueryWrapper<DocumentJobRecord> libraryQuery(Long userId) {
        return new LambdaQueryWrapper<DocumentJobRecord>()
                .eq(DocumentJobRecord::getUserId, userId)
                .and(wrapper -> wrapper
                        .ne(DocumentJobRecord::getSourceFeature, "DESIGN_PACKAGE")
                        .or(designPackage -> designPackage
                                .eq(DocumentJobRecord::getSourceFeature, "DESIGN_PACKAGE")
                                .and(zip -> zip
                                        .eq(DocumentJobRecord::getMode, "zip")
                                        .or()
                                        .like(DocumentJobRecord::getFileName, ".zip"))));
    }

    private DocumentLibraryItemVO toLibraryItem(DocumentJobRecord record) {
        DocumentLibraryItemVO item = new DocumentLibraryItemVO();
        item.setId(record.getJobId());
        item.setProjectName(projectName(record));
        item.setCreateTime(record.getCreatedAt());
        item.setStatus(record.getStatus());
        String downloadUrl = "/api/documents/" + record.getJobId() + "/download";
        if (isPackage(record)) {
            item.setPackageUrl(downloadUrl);
        } else {
            setDocUrl(item.getDoc(), record, downloadUrl);
        }
        return item;
    }

    private boolean isPackage(DocumentJobRecord record) {
        return "DESIGN_PACKAGE".equals(record.getSourceFeature())
                && (equalsIgnoreCase(record.getMode(), "zip") || endsWithIgnoreCase(record.getFileName(), ".zip"));
    }

    private void setDocUrl(DocumentLibraryItemVO.DocOutputVO doc, DocumentJobRecord record, String downloadUrl) {
        String mode = record.getMode() == null ? "" : record.getMode();
        String fileName = record.getFileName() == null ? "" : record.getFileName();
        if (equalsIgnoreCase(mode, "rewrite")) {
            doc.setReduceDocUrl(downloadUrl);
        } else if (equalsIgnoreCase(mode, "double")) {
            doc.setDoubleReduceDocUrl(downloadUrl);
        } else if (equalsIgnoreCase(mode, "humanize") || "REWRITE".equals(record.getSourceFeature())) {
            doc.setAiReduceDocUrl(downloadUrl);
        } else if (fileName.toLowerCase().endsWith(".pdf") && (fileName.contains("AI") || fileName.contains("检测") || fileName.contains("AIGC"))) {
            doc.setAiReportUrl(downloadUrl);
        } else if (fileName.toLowerCase().endsWith(".pdf") && (fileName.contains("查重") || fileName.contains("重复"))) {
            doc.setPlagiarismReportUrl(downloadUrl);
        }
    }

    private String projectName(DocumentJobRecord record) {
        if ("DESIGN_PACKAGE".equals(record.getSourceFeature()) && record.getMessage() != null) {
            int index = record.getMessage().indexOf(" 成果文件已生成");
            if (index > 0) {
                return record.getMessage().substring(0, index);
            }
        }
        String name = record.getFileName() == null || record.getFileName().isBlank() ? "未命名项目" : record.getFileName();
        return name.replaceAll("(?i)\\.(docx|pdf|zip)$", "");
    }

    private boolean equalsIgnoreCase(String value, String expected) {
        return value != null && value.equalsIgnoreCase(expected);
    }

    private boolean endsWithIgnoreCase(String value, String suffix) {
        return value != null && value.toLowerCase().endsWith(suffix.toLowerCase());
    }
}
