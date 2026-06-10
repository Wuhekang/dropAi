package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.config.DoubaoProperties;
import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.service.DocumentRewriteService;
import com.dropai.rewrite.service.AiRewriteService;
import com.dropai.rewrite.service.WorkflowRewriteService;
import com.dropai.rewrite.vo.DocumentParagraphJobVO;
import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import jakarta.annotation.PreDestroy;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DocumentRewriteServiceImpl implements DocumentRewriteService {

    private final WorkflowRewriteService workflowRewriteService;
    private final AiRewriteService aiRewriteService;
    private final DoubaoProperties doubaoProperties;
    private final DocumentJobMapper documentJobMapper;
    private final ObjectMapper objectMapper;
    private final Map<String, DocumentRewriteJobVO> jobs = new ConcurrentHashMap<>();
    private final Path uploadDir = Path.of("storage", "uploads");
    private final Path outputDir = Path.of("storage", "outputs");
    private final Path jobLogDir = Path.of("storage", "jobs");
    private final ExecutorService documentExecutor = Executors.newFixedThreadPool(2);

    public DocumentRewriteServiceImpl(
            WorkflowRewriteService workflowRewriteService,
            AiRewriteService aiRewriteService,
            DoubaoProperties doubaoProperties,
            DocumentJobMapper documentJobMapper,
            ObjectMapper objectMapper
    ) {
        this.workflowRewriteService = workflowRewriteService;
        this.aiRewriteService = aiRewriteService;
        this.doubaoProperties = doubaoProperties;
        this.documentJobMapper = documentJobMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public DocumentRewriteJobVO submit(MultipartFile file, String mode, String platform) {
        Long userId = AuthContext.requireUserId();
        Long running = documentJobMapper.selectCount(new LambdaQueryWrapper<DocumentJobRecord>()
                .eq(DocumentJobRecord::getUserId, userId)
                .in(DocumentJobRecord::getStatus, "PENDING", "RUNNING"));
        if (running >= 2) {
            throw new IllegalStateException("每个账号最多同时处理 2 个文档，请等待当前任务完成");
        }
        String originalName = file.getOriginalFilename() == null ? "document.docx" : file.getOriginalFilename();
        String normalizedMode = normalizeMode(mode);
        String normalizedPlatform = normalizePlatform(platform);
        if (!originalName.toLowerCase().endsWith(".docx")) {
            throw new IllegalArgumentException("当前仅支持上传 .docx 文件");
        }

        try {
            Files.createDirectories(uploadDir);
            Files.createDirectories(outputDir);
            Files.createDirectories(jobLogDir);
            String jobId = UUID.randomUUID().toString().replace("-", "");
            Path inputPath = uploadDir.resolve(jobId + "-" + originalName);
            file.transferTo(inputPath);

            DocumentRewriteJobVO job = new DocumentRewriteJobVO();
            job.setJobId(jobId);
            job.setFileName(originalName);
            job.setMode(normalizedMode);
            job.setModeName(modeName(normalizedMode));
            job.setPlatform(normalizedPlatform);
            job.setPlatformName(platformName(normalizedPlatform));
            job.setStatus("PENDING");
            job.setMessage("文档已上传，等待执行：" + job.getModeName() + " / " + job.getPlatformName());
            job.setCreatedAt(LocalDateTime.now());
            job.setUpdatedAt(LocalDateTime.now());
            jobs.put(jobId, job);
            persistJob(job, userId, null);

            documentExecutor.submit(() -> process(jobId, inputPath, outputDir.resolve(jobId + "-ai-optimized.docx")));
            return job;
        } catch (IOException exception) {
            throw new IllegalStateException("文档上传失败：" + exception.getMessage(), exception);
        }
    }

    @Override
    public DocumentRewriteJobVO getJob(String jobId) {
        Long userId = AuthContext.requireUserId();
        DocumentRewriteJobVO job = jobs.get(jobId);
        DocumentJobRecord record = ownedRecord(jobId, userId);
        if (record == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        return job == null ? toJob(record) : job;
    }

    @Override
    public List<DocumentRewriteJobVO> listJobs() {
        Long userId = AuthContext.requireUserId();
        return documentJobMapper.selectList(new LambdaQueryWrapper<DocumentJobRecord>()
                        .eq(DocumentJobRecord::getUserId, userId)
                        .orderByDesc(DocumentJobRecord::getCreatedAt))
                .stream().map(this::toJob)
                .toList();
    }

    @Override
    public Resource download(String jobId) {
        DocumentJobRecord record = ownedRecord(jobId, AuthContext.requireUserId());
        if (record == null || record.getOutputFile() == null || record.getOutputFile().length == 0) {
            throw new IllegalArgumentException("处理结果不存在或任务尚未完成");
        }
        return new ByteArrayResource(record.getOutputFile());
    }

    @Override
    public String downloadFileName(String jobId) {
        DocumentRewriteJobVO job = getJob(jobId);
        String baseName = job.getFileName().replaceAll("(?i)\\.docx$", "");
        return baseName + "-AI痕迹优化.docx";
    }

    public void process(String jobId, Path inputPath, Path outputPath) {
        DocumentRewriteJobVO job = jobs.get(jobId);
        update(job, "RUNNING", "正在分析文档段落");
        try (InputStream inputStream = Files.newInputStream(inputPath);
             XWPFDocument document = new XWPFDocument(inputStream)) {
            List<XWPFParagraph> paragraphs = collectParagraphs(document);
            job.setTotalParagraphs(paragraphs.size());
            job.setUpdatedAt(LocalDateTime.now());
            if (paragraphs.isEmpty()) {
                job.setMessage("未识别到普通正文段落，将生成原文副本");
            }

            List<RewriteTarget> targets = collectRewriteTargets(job, paragraphs);
            job.setParagraphs(targets.stream().map(this::toParagraphJob).collect(Collectors.toList()));
            job.setProcessedParagraphs(0);
            job.setTotalParagraphs(targets.size());
            if (targets.isEmpty()) {
                update(job, "FAILED", "未识别到可优化正文段落，未生成优化结果。请检查文档是否包含目录后的正文内容。");
                return;
            }
            job.setMessage("已提取 " + targets.size() + " 个待优化段落，开始并发处理");
            job.setUpdatedAt(LocalDateTime.now());

            List<RewriteResult> results = rewriteTargetsConcurrently(job, targets);
            long failedParagraphs = results.stream().filter(result -> !result.success()).count();
            String firstFailure = results.stream()
                    .filter(result -> !result.success())
                    .map(RewriteResult::errorMessage)
                    .filter(message -> message != null && !message.isBlank())
                    .findFirst()
                    .orElse("");
            results.stream()
                    .sorted(Comparator.comparingInt(RewriteResult::index))
                    .forEach(result -> {
                        if (result.success() && result.rewrittenText() != null && !result.rewrittenText().isBlank()) {
                            replaceParagraphText(result.paragraph(), result.rewrittenText());
                            job.setRewrittenParagraphs(job.getRewrittenParagraphs() + 1);
                        }
                    });
            try (OutputStream outputStream = Files.newOutputStream(outputPath)) {
                document.write(outputStream);
            }
            job.setDownloadUrl("/api/document/rewrite/download/" + jobId);
            update(job, "SUCCESS", job.getModeName() + "完成，目录后正文已处理 " + job.getRewrittenParagraphs()
                    + " 个段落；模型状态：" + aiRewriteService.lastCallProvider());
            if (failedParagraphs > 0) {
                update(job, "FAILED", job.getModeName() + "未完成：成功改写 " + job.getRewrittenParagraphs()
                        + " 个段落，失败 " + failedParagraphs + " 个段落；模型状态：" + aiRewriteService.lastCallProvider());
            } else {
                update(job, "SUCCESS", job.getModeName() + "完成，正文已成功改写 " + job.getRewrittenParagraphs()
                        + " 个段落；模型状态：" + aiRewriteService.lastCallProvider());
            }
            if (failedParagraphs > 0) {
                update(job, "FAILED", job.getModeName() + "未完成：成功改写 " + job.getRewrittenParagraphs()
                        + " 个段落，失败 " + failedParagraphs + " 个段落；首个失败原因：" + firstFailure);
            } else {
                update(job, "SUCCESS", job.getModeName() + "完成，正文已成功改写 " + job.getRewrittenParagraphs()
                        + " 个段落；模型状态：" + aiRewriteService.lastCallProvider());
            }
            if (failedParagraphs > 0) {
                update(job, "FAILED", "Document rewrite failed: rewritten=" + job.getRewrittenParagraphs()
                        + ", failed=" + failedParagraphs + ", firstError=" + firstFailure);
            } else {
                update(job, "SUCCESS", "Document rewrite completed: rewritten=" + job.getRewrittenParagraphs()
                        + ", provider=" + aiRewriteService.lastCallProvider());
            }
            persistJob(job, null, Files.readAllBytes(outputPath));
        } catch (Exception exception) {
            writeJobLog(jobId, exception);
            update(job, "FAILED", "处理失败：" + readableMessage(exception) + "；详情见 storage/jobs/" + jobId + ".log");
            persistJob(job, null, null);
        }
    }

    private boolean shouldRewriteBodyParagraph(XWPFParagraph paragraph, String text, String mode) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.length() < 8) {
            return false;
        }
        if (isHeadingParagraph(paragraph, trimmed)) {
            return false;
        }
        if (isCatalogLine(trimmed) || isProtectedSectionTitle(trimmed)) {
            return false;
        }
        if ("PRECISE_AI_REDUCE".equals(mode)) {
            return hasAiTraceSignal(trimmed) && !isTechnicalFragment(trimmed);
        }
        return true;
    }

    private boolean isTechnicalFragment(String text) {
        String compact = text.replaceAll("\\s+", " ").trim();
        String lower = compact.toLowerCase();
        if (compact.length() <= 40 && hasAny(lower,
                "varchar", "bigint", "timestamp", "int", "decimal", "datetime",
                "字段名称", "字段说明", "默认值", "主键", "外键", "类型", "长度", "not null",
                "auto_increment", "current_timestamp")) {
            return true;
        }
        if (compact.matches("^@\\w+(Mapping|Autowired|Resource|Override|Service|Controller|Entity|Table).*")) {
            return true;
        }
        if (hasAny(compact,
                "public ", "private ", "protected ", "class ", "interface ", "return ",
                "if (", "else", "for (", "while (", "try {", "catch (", "new ",
                "String ", "Integer ", "Long ", "Float ", "Double ", "Boolean ",
                "queryWrapper.", "Result.", ".get", ".set")) {
            int codeMarks = countCodeMarks(compact);
            if (codeMarks >= 2 || compact.endsWith(";") || compact.endsWith("{") || compact.endsWith("}")) {
                return true;
            }
        }
        if (compact.matches("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*|\\([^)]*\\)|;|\\{|\\}).*")) {
            return true;
        }
        if (compact.matches("^[/A-Za-z0-9_{}.$\"'=<>!+\\-*/(),;:\\[\\] ]+$") && countCodeMarks(compact) >= 2) {
            return true;
        }
        return false;
    }

    private boolean hasAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private int countCodeMarks(String text) {
        int count = 0;
        for (char ch : text.toCharArray()) {
            if ("{}();=@\"'./<>[]".indexOf(ch) >= 0) {
                count++;
            }
        }
        return count;
    }

    private List<RewriteTarget> collectRewriteTargets(DocumentRewriteJobVO job, List<XWPFParagraph> paragraphs) {
        List<RewriteTarget> targets = new ArrayList<>();
        boolean hasCatalog = hasCatalog(paragraphs);
        boolean afterCatalog = !hasCatalog;
        for (int index = 0; index < paragraphs.size(); index++) {
            XWPFParagraph paragraph = paragraphs.get(index);
            String text = paragraph.getText();
            if (!afterCatalog) {
                afterCatalog = isCatalogEnd(text);
                continue;
            }
            if (shouldRewriteBodyParagraph(paragraph, text, job.getMode())) {
                targets.add(new RewriteTarget(index, paragraph, text.trim()));
            }
        }
        return targets;
    }

    private List<RewriteResult> rewriteTargetsConcurrently(DocumentRewriteJobVO job, List<RewriteTarget> targets) throws Exception {
        List<RewriteResult> results = new ArrayList<>();
        if (targets.isEmpty()) {
            return results;
        }

        int concurrency = Math.max(1, Math.min(doubaoProperties.getDocumentConcurrency(), 64));
        concurrency = Math.min(concurrency, targets.size());
        ExecutorService paragraphExecutor = Executors.newFixedThreadPool(concurrency);
        CompletionService<RewriteResult> completionService = new ExecutorCompletionService<>(paragraphExecutor);
        try {
            for (RewriteTarget target : targets) {
                completionService.submit(() -> {
                    updateParagraphStatus(job, target.index(), "RUNNING", null, "正在改写");
                    try {
                        String rewritten = rewriteByMode(target.text(), job.getMode(), job.getPlatform());
                        updateParagraphStatus(job, target.index(), "SUCCESS", rewritten, "已完成");
                        return new RewriteResult(
                                target.index(),
                                target.paragraph(),
                                rewritten,
                                true,
                                ""
                        );
                    } catch (Exception exception) {
                        updateParagraphStatus(job, target.index(), "FAILED", target.text(), "处理失败，保留原文：" + readableMessage(exception));
                        String message = readableMessage(exception);
                        return new RewriteResult(target.index(), target.paragraph(), target.text(), false, message);
                    }
                });
            }

            for (int completed = 0; completed < targets.size(); completed++) {
                Future<RewriteResult> future = completionService.take();
                RewriteResult result = future.get();
                results.add(result);
                job.setProcessedParagraphs(completed + 1);
                job.setMessage("并发优化中：" + job.getProcessedParagraphs() + "/" + targets.size()
                        + " 个待优化段落，线程数 " + concurrency);
                job.setUpdatedAt(LocalDateTime.now());
            }
        } finally {
            paragraphExecutor.shutdown();
        }
        return results;
    }

    private List<XWPFParagraph> collectParagraphs(XWPFDocument document) {
        List<XWPFParagraph> paragraphs = new java.util.ArrayList<>(document.getParagraphs());
        for (XWPFTable table : document.getTables()) {
            collectTableParagraphs(table, paragraphs);
        }
        return paragraphs;
    }

    private void collectTableParagraphs(XWPFTable table, List<XWPFParagraph> paragraphs) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                paragraphs.addAll(cell.getParagraphs());
                for (XWPFTable nestedTable : cell.getTables()) {
                    collectTableParagraphs(nestedTable, paragraphs);
                }
            }
        }
    }

    private String rewriteByMode(String text, String mode, String platform) {
        String suffix = platform == null || "GENERAL".equals(platform) ? "" : "@" + platform;
        if ("DUPLICATE_REDUCE".equals(mode)) {
            return workflowRewriteService.execute(text, "降重复改写").getRewrittenText();
        }
        if ("DOUBLE_REDUCE".equals(mode)) {
            return workflowRewriteService.execute(text, "双降" + suffix).getRewrittenText();
        }
        if ("FULL_AI_REDUCE".equals(mode)) {
            return workflowRewriteService.execute(text, "深度降低AI写作痕迹" + suffix).getRewrittenText();
        }
        return workflowRewriteService.execute(text, "降低AI写作痕迹" + suffix).getRewrittenText();
    }

    private String normalizeMode(String mode) {
        if ("DUPLICATE_REDUCE".equals(mode)
                || "DOUBLE_REDUCE".equals(mode)
                || "PRECISE_AI_REDUCE".equals(mode)) {
            return mode;
        }
        return "FULL_AI_REDUCE";
    }

    private String modeName(String mode) {
        return switch (mode) {
            case "DUPLICATE_REDUCE" -> "智能降重";
            case "DOUBLE_REDUCE" -> "双降";
            case "PRECISE_AI_REDUCE" -> "精准降AI";
            default -> "全文降AI";
        };
    }

    private String normalizePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return "GENERAL";
        }
        return switch (platform.trim().toUpperCase()) {
            case "CNKI", "WEIPU", "WANFANG", "GEZIDA" -> platform.trim().toUpperCase();
            default -> "GENERAL";
        };
    }

    private String platformName(String platform) {
        return switch (platform) {
            case "CNKI" -> "知网";
            case "WEIPU" -> "维普";
            case "WANFANG" -> "万方";
            case "GEZIDA" -> "格子达";
            default -> "通用";
        };
    }

    private boolean hasAiTraceSignal(String text) {
        return text.contains("首先")
                || text.contains("其次")
                || text.contains("最后")
                || text.contains("综上所述")
                || text.contains("值得注意的是")
                || text.contains("随着")
                || text.contains("因此")
                || text.contains("由此可见");
    }

    private boolean isProtectedSectionTitle(String text) {
        return text.matches("^(参考文献|致谢|附录|作者简介|声明)$");
    }

    private boolean hasCatalog(List<XWPFParagraph> paragraphs) {
        return paragraphs.stream()
                .map(XWPFParagraph::getText)
                .filter(text -> text != null)
                .map(String::trim)
                .anyMatch(text -> "目录".equals(text) || "目 录".equals(text) || isCatalogLine(text));
    }

    private boolean isCatalogEnd(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if ("目录".equals(trimmed) || "目 录".equals(trimmed)) {
            return false;
        }
        return isCommonBodyStartTitle(trimmed) || (isHeadingLikeNumberedTitle(trimmed) && !isCatalogLine(trimmed));
    }

    private boolean isHeadingParagraph(XWPFParagraph paragraph, String text) {
        String style = paragraph.getStyle();
        if (style != null && style.toLowerCase().contains("heading")) {
            return true;
        }
        return isHeadingLikeNumberedTitle(text);
    }

    private boolean isCommonBodyStartTitle(String text) {
        return text.matches("^(摘要|摘 要|Abstract|ABSTRACT|引言|绪论|前言|正文)$")
                || text.matches("^第[一二三四五六七八九十百]+章.*$");
    }

    private boolean isHeadingLikeNumberedTitle(String text) {
        return text.matches("^(第[一二三四五六七八九十百]+[章节篇].*)$")
                || text.matches("^([一二三四五六七八九十]+、).{1,40}$")
                || text.matches("^(\\d+(\\.\\d+){0,3}\\s+).{1,60}$");
    }

    private boolean isCatalogLine(String text) {
        return text.matches("^.+[\\.·•…]{2,}\\s*\\d+\\s*$")
                || text.matches("^.+\\s+\\d+\\s*$")
                || text.contains("TOC \\o");
    }

    private void replaceParagraphText(XWPFParagraph paragraph, String text) {
        XWPFRun templateRun = paragraph.getRuns().isEmpty() ? null : paragraph.getRuns().get(0);
        CTRPr runProperties = null;
        if (templateRun != null && templateRun.getCTR().isSetRPr()) {
            runProperties = (CTRPr) templateRun.getCTR().getRPr().copy();
        }
        int runCount = paragraph.getRuns().size();
        for (int index = runCount - 1; index >= 0; index--) {
            paragraph.removeRun(index);
        }
        XWPFRun run = paragraph.createRun();
        if (runProperties != null) {
            run.getCTR().setRPr(runProperties);
        }
        run.setText(text == null ? "" : text);
    }

    private String readableMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 160 ? message.substring(0, 160) + "..." : message;
    }

    private void writeJobLog(String jobId, Exception exception) {
        try {
            Files.createDirectories(jobLogDir);
            StringWriter stringWriter = new StringWriter();
            exception.printStackTrace(new PrintWriter(stringWriter));
            Files.writeString(jobLogDir.resolve(jobId + ".log"), stringWriter.toString());
        } catch (IOException ignored) {
            // The job message still carries the user-facing failure state.
        }
    }

    private void update(DocumentRewriteJobVO job, String status, String message) {
        job.setStatus(status);
        job.setMessage(message);
        job.setUpdatedAt(LocalDateTime.now());
    }

    private DocumentJobRecord ownedRecord(String jobId, Long userId) {
        return documentJobMapper.selectOne(new LambdaQueryWrapper<DocumentJobRecord>()
                .eq(DocumentJobRecord::getJobId, jobId)
                .eq(DocumentJobRecord::getUserId, userId));
    }

    private void persistJob(DocumentRewriteJobVO job, Long userId, byte[] outputFile) {
        if (job == null) return;
        DocumentJobRecord record = documentJobMapper.selectById(job.getJobId());
        if (record == null) {
            record = new DocumentJobRecord();
            record.setJobId(job.getJobId());
            record.setUserId(userId);
            record.setCreatedAt(job.getCreatedAt());
        }
        record.setFileName(job.getFileName());
        record.setMode(job.getMode());
        record.setModeName(job.getModeName());
        record.setPlatform(job.getPlatform());
        record.setPlatformName(job.getPlatformName());
        record.setStatus(job.getStatus());
        record.setTotalParagraphs(job.getTotalParagraphs());
        record.setProcessedParagraphs(job.getProcessedParagraphs());
        record.setRewrittenParagraphs(job.getRewrittenParagraphs());
        record.setMessage(job.getMessage());
        record.setUpdatedAt(job.getUpdatedAt());
        try {
            record.setParagraphsJson(objectMapper.writeValueAsString(job.getParagraphs()));
        } catch (Exception ignored) {
            record.setParagraphsJson("[]");
        }
        if (outputFile != null) record.setOutputFile(outputFile);
        if (documentJobMapper.selectById(job.getJobId()) == null) documentJobMapper.insert(record);
        else documentJobMapper.updateById(record);
    }

    private DocumentRewriteJobVO toJob(DocumentJobRecord record) {
        DocumentRewriteJobVO job = new DocumentRewriteJobVO();
        job.setJobId(record.getJobId());
        job.setFileName(record.getFileName());
        job.setMode(record.getMode());
        job.setModeName(record.getModeName());
        job.setPlatform(record.getPlatform());
        job.setPlatformName(record.getPlatformName());
        job.setStatus(record.getStatus());
        job.setTotalParagraphs(record.getTotalParagraphs() == null ? 0 : record.getTotalParagraphs());
        job.setProcessedParagraphs(record.getProcessedParagraphs() == null ? 0 : record.getProcessedParagraphs());
        job.setRewrittenParagraphs(record.getRewrittenParagraphs() == null ? 0 : record.getRewrittenParagraphs());
        job.setMessage(record.getMessage());
        job.setCreatedAt(record.getCreatedAt());
        job.setUpdatedAt(record.getUpdatedAt());
        if (record.getOutputFile() != null) job.setDownloadUrl("/api/document/rewrite/download/" + record.getJobId());
        try {
            job.setParagraphs(objectMapper.readValue(record.getParagraphsJson(), new TypeReference<List<DocumentParagraphJobVO>>() {}));
        } catch (Exception ignored) {
            job.setParagraphs(new ArrayList<>());
        }
        return job;
    }

    private DocumentParagraphJobVO toParagraphJob(RewriteTarget target) {
        DocumentParagraphJobVO vo = new DocumentParagraphJobVO();
        vo.setIndex(target.index());
        vo.setStatus("PENDING");
        vo.setOriginalText(target.text());
        vo.setMessage("等待处理");
        return vo;
    }

    private void updateParagraphStatus(DocumentRewriteJobVO job, int index, String status, String rewrittenText, String message) {
        for (DocumentParagraphJobVO paragraph : job.getParagraphs()) {
            if (paragraph.getIndex() == index) {
                paragraph.setStatus(status);
                paragraph.setMessage(message);
                if (rewrittenText != null) {
                    paragraph.setRewrittenText(rewrittenText);
                }
                job.setUpdatedAt(LocalDateTime.now());
                return;
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        documentExecutor.shutdown();
    }

    private record RewriteTarget(int index, XWPFParagraph paragraph, String text) {
    }

    private record RewriteResult(int index, XWPFParagraph paragraph, String rewrittenText, boolean success, String errorMessage) {
    }
}
