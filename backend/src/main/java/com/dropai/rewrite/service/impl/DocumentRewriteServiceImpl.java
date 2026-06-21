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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

    @PostConstruct
    public void markInterruptedJobsFailed() {
        try {
            List<DocumentJobRecord> interruptedJobs = documentJobMapper.selectList(new LambdaQueryWrapper<DocumentJobRecord>()
                    .in(DocumentJobRecord::getStatus, "PENDING", "RUNNING"));
            for (DocumentJobRecord record : interruptedJobs) {
                record.setStatus("FAILED");
                record.setMessage("服务重启后任务未继续执行，请重新上传文档处理");
                record.setUpdatedAt(LocalDateTime.now());
                documentJobMapper.updateById(record);
            }
        } catch (Exception ignored) {
            // Some local/test databases may not have the document_job table yet.
        }
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
            job.setSourceFeature("REWRITE");
            job.setMode(normalizedMode);
            job.setModeName(modeName(normalizedMode));
            job.setPlatform(normalizedPlatform);
            job.setPlatformName(platformName(normalizedPlatform));
            job.setStatus("PENDING");
            job.setMessage("文档已上传，等待执行：" + processingMessage(normalizedMode) + " / " + job.getPlatformName());
            job.setCreatedAt(LocalDateTime.now());
            job.setUpdatedAt(LocalDateTime.now());
            jobs.put(jobId, job);
            persistJob(job, userId, null);

            scheduleProcessingAfterCommit(jobId, inputPath, outputDir.resolve(jobId + "-ai-optimized.docx"));
            return job;
        } catch (IOException exception) {
            throw new IllegalStateException("文档上传失败：" + exception.getMessage(), exception);
        }
    }

    private void scheduleProcessingAfterCommit(String jobId, Path inputPath, Path outputPath) {
        Runnable task = () -> documentExecutor.submit(() -> processSafely(jobId, inputPath, outputPath));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
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
        DocumentJobRecord record = ownedRecordWithOutputFile(jobId, AuthContext.requireUserId());
        if (record == null || record.getOutputFile() == null || record.getOutputFile().length == 0) {
            throw new IllegalArgumentException("处理结果不存在或任务尚未完成");
        }
        return new ByteArrayResource(record.getOutputFile());
    }

    @Override
    public String downloadFileName(String jobId) {
        DocumentRewriteJobVO job = getJob(jobId);
        if (!"REWRITE".equals(job.getSourceFeature())) {
            return job.getFileName();
        }
        String baseName = job.getFileName().replaceAll("(?i)\\.docx$", "");
        return baseName + "-AI痕迹优化.docx";
    }

    private void processSafely(String jobId, Path inputPath, Path outputPath) {
        try {
            process(jobId, inputPath, outputPath);
        } catch (Exception exception) {
            writeJobLog(jobId, exception);
            DocumentRewriteJobVO job = jobs.get(jobId);
            update(job, "FAILED", "处理线程异常：" + readableMessage(exception) + "；详情见 storage/jobs/" + jobId + ".log");
            persistJobSafely(job, null, null);
        }
    }

    public void process(String jobId, Path inputPath, Path outputPath) {
        DocumentRewriteJobVO job = jobs.get(jobId);
        update(job, "RUNNING", "正在读取 DOCX 内容，图片较多时可能需要更长时间");
        try (InputStream inputStream = Files.newInputStream(inputPath);
             XWPFDocument document = new XWPFDocument(inputStream)) {
            update(job, "RUNNING", "DOCX 已读取，正在提取文档段落");
            List<XWPFParagraph> paragraphs = collectParagraphs(document);
            job.setTotalParagraphs(paragraphs.size());
            job.setProcessedParagraphs(0);
            job.setUpdatedAt(LocalDateTime.now());
            if (paragraphs.isEmpty()) {
                job.setMessage("未识别到普通正文段落，将生成原文副本");
            }
            update(job, "RUNNING", "已读取 " + paragraphs.size() + " 个文档段落，正在筛选正文段落");

            List<RewriteTarget> targets = collectRewriteTargets(job, paragraphs);
            job.setParagraphs(targets.stream().map(this::toParagraphJob).collect(Collectors.toList()));
            job.setProcessedParagraphs(0);
            job.setTotalParagraphs(targets.size());
            if (targets.isEmpty()) {
                update(job, "FAILED", "未识别到可优化正文段落，未生成优化结果。请检查文档是否包含目录后的正文内容。");
                return;
            }
            update(job, "RUNNING", "已提取 " + targets.size() + " 个待优化段落，开始并发处理");

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
            if (failedParagraphs > 0) {
                update(job, "FAILED", job.getModeName() + "未完成：已处理 " + job.getRewrittenParagraphs()
                        + " 个段落，失败 " + failedParagraphs + " 个段落；首个失败原因：" + firstFailure
                        + "；模型状态：" + aiRewriteService.lastCallProvider());
            } else {
                update(job, "SUCCESS", completedMessage(job.getMode()) + "；已处理 " + job.getRewrittenParagraphs()
                        + " 个段落；模型状态：" + aiRewriteService.lastCallProvider());
            }
            persistJob(job, null, Files.readAllBytes(outputPath));
        } catch (Exception exception) {
            writeJobLog(jobId, exception);
            update(job, "FAILED", "处理失败：" + readableMessage(exception) + "；详情见 storage/jobs/" + jobId + ".log");
            persistJobSafely(job, null, null);
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
        if ("rewrite".equals(mode)) {
            return workflowRewriteService.execute(text, "rewrite" + suffix).getRewrittenText();
        }
        if ("double".equals(mode)) {
            String rewritten = workflowRewriteService.execute(text, "rewrite" + suffix).getRewrittenText();
            return workflowRewriteService.execute(rewritten, "humanize" + suffix).getRewrittenText();
        }
        return workflowRewriteService.execute(text, "humanize" + suffix).getRewrittenText();
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "humanize";
        }
        return switch (mode.trim()) {
            case "rewrite", "DUPLICATE_REDUCE" -> "rewrite";
            case "double", "DOUBLE_REDUCE" -> "double";
            case "humanize", "FULL_AI_REDUCE", "PRECISE_AI_REDUCE" -> "humanize";
            default -> "humanize";
        };
    }

    private String modeName(String mode) {
        return switch (mode) {
            case "rewrite" -> "智能降重";
            case "double" -> "双降增强";
            default -> "智能降AI";
        };
    }

    private String modeTarget(String mode) {
        return switch (mode) {
            case "rewrite" -> "降低重复表达风险，同时尽量保持论文结构、数据、引用、图表编号不变。";
            case "double" -> "第一遍降重，第二遍降AI，即调用两次处理流程。";
            default -> "降低AI检测风险，同时保持学术表达和原文事实不变。";
        };
    }

    private String processingMessage(String mode) {
        return modeName(mode) + "处理中：" + modeTarget(mode);
    }

    private String completedMessage(String mode) {
        if ("rewrite".equals(mode)) {
            return "已完成降重处理，建议结合检测报告进一步微调";
        }
        if ("double".equals(mode)) {
            return "已完成降重/降AI处理，建议结合检测报告进一步微调";
        }
        return "已完成学术表达优化，建议结合检测报告进一步微调";
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
        if (job == null) {
            return;
        }
        job.setStatus(status);
        job.setMessage(message);
        job.setUpdatedAt(LocalDateTime.now());
        persistJobSafely(job, null, null);
    }

    private void persistJobSafely(DocumentRewriteJobVO job, Long userId, byte[] outputFile) {
        try {
            persistJob(job, userId, outputFile);
        } catch (Exception exception) {
            if (job != null) {
                writeJobLog(job.getJobId() + "-persist", exception);
            }
        }
    }

    private DocumentJobRecord ownedRecord(String jobId, Long userId) {
        return documentJobMapper.selectOne(new LambdaQueryWrapper<DocumentJobRecord>()
                .eq(DocumentJobRecord::getJobId, jobId)
                .eq(DocumentJobRecord::getUserId, userId));
    }

    private DocumentJobRecord ownedRecordWithOutputFile(String jobId, Long userId) {
        return documentJobMapper.selectOne(new LambdaQueryWrapper<DocumentJobRecord>()
                .select(DocumentJobRecord::getJobId,
                        DocumentJobRecord::getUserId,
                        DocumentJobRecord::getFileName,
                        DocumentJobRecord::getOutputFile)
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
        record.setSourceFeature(job.getSourceFeature() == null ? "REWRITE" : job.getSourceFeature());
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
        job.setSourceFeature(record.getSourceFeature() == null ? "REWRITE" : record.getSourceFeature());
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
