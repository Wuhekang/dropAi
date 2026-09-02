package com.dropai.rewrite.external;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.service.DocumentCharacterCountService;
import com.dropai.rewrite.service.PointService;
import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Isolated document route for the opt-in Daya profile.
 *
 * <p>The class name and endpoint are retained for compatibility with the already-added UI and
 * state table. Runtime processing is local: it uses the desktop Doubao configuration plus the
 * selected application Skill and never contacts the historical Xuejie server.</p>
 */
@Service
public class XuejieExternalDocumentRewriteService {
    private static final Logger log = LoggerFactory.getLogger(XuejieExternalDocumentRewriteService.class);
    private static final String SOURCE_FEATURE = "REWRITE";

    private final PlatformDoubaoDocumentProcessor processor;
    private final XuejieDocxValidator docxValidator;
    private final XuejieExternalPointRefundService refundService;
    private final XuejieExternalJobStateRepository stateRepository;
    private final DocumentJobMapper documentJobMapper;
    private final DocumentCharacterCountService characterCountService;
    private final PointService pointService;
    private final Path uploadDir = Path.of("storage", "uploads");
    private final Path outputDir = Path.of("storage", "outputs");
    private final ExecutorService executor = Executors.newFixedThreadPool(2, daemonThreadFactory());

    public XuejieExternalDocumentRewriteService(PlatformDoubaoDocumentProcessor processor,
                                                XuejieDocxValidator docxValidator,
                                                XuejieExternalPointRefundService refundService,
                                                XuejieExternalJobStateRepository stateRepository,
                                                DocumentJobMapper documentJobMapper,
                                                DocumentCharacterCountService characterCountService,
                                                PointService pointService) {
        this.processor = processor;
        this.docxValidator = docxValidator;
        this.refundService = refundService;
        this.stateRepository = stateRepository;
        this.documentJobMapper = documentJobMapper;
        this.characterCountService = characterCountService;
        this.pointService = pointService;
    }

    @Transactional
    public DocumentRewriteJobVO submit(MultipartFile file, String modeValue,
                                       String platformValue, String requestId) {
        Long userId = AuthContext.requireUserId();
        XuejieRewriteMode mode = XuejieRewriteMode.require(modeValue);
        XuejiePlatform platform = XuejiePlatform.require(platformValue);
        String jobId = jobId(userId, requestId);
        DocumentJobRecord existing = documentJobMapper.selectById(jobId);
        if (existing != null) return existingJob(existing, userId, mode, platform);

        if (!processor.configured()) {
            throw new IllegalStateException("未配置 DOUBAO_API_KEY，请在桌面 .env 中配置");
        }
        String originalName = safeOriginalName(file == null ? null : file.getOriginalFilename());
        docxValidator.validateUpload(file);
        if (!originalName.toLowerCase().endsWith(".docx")) {
            throw new IllegalArgumentException("当前仅支持上传 .docx 文件");
        }
        Long running = documentJobMapper.selectCount(new LambdaQueryWrapper<DocumentJobRecord>()
                .eq(DocumentJobRecord::getUserId, userId)
                .in(DocumentJobRecord::getStatus, "PENDING", "RUNNING"));
        if (running != null && running >= 2) {
            throw new IllegalStateException("每个账号最多同时处理 2 个文档，请等待当前任务完成");
        }

        Path inputPath = uploadDir.resolve(jobId + "-platform-source.docx");
        Path temporaryInput = uploadDir.resolve(jobId + "-" + UUID.randomUUID().toString().replace("-", "") + ".upload");
        boolean canonicalInputCreated = false;
        try {
            Files.createDirectories(uploadDir);
            Files.createDirectories(outputDir);
            int charCount = characterCountService.countFromAbstractOrCatalog(file);
            file.transferTo(temporaryInput);
            docxValidator.validateStagedFile(temporaryInput);
            String featureCode = featureCode(mode);
            String featureName = featureName(mode);
            int costPoints = pointService.usageCostPoints(featureCode, charCount);
            pointService.deductCustom(userId, jobId, featureCode, featureName, costPoints,
                    "提交平台适配文档任务：" + originalName + " / " + platform.remoteName());

            DocumentJobRecord record = initialRecord(jobId, userId, originalName, mode, platform,
                    charCount, costPoints);
            documentJobMapper.insert(record);
            stateRepository.insert(XuejieExternalJobStateRepository.State.created(jobId, userId, originalName,
                    platform, mode, featureCode, featureName, costPoints));
            moveStagedInput(temporaryInput, inputPath);
            canonicalInputCreated = true;
            scheduleAfterCommit(() -> executor.submit(() -> process(jobId, inputPath, mode, platform,
                    featureCode, featureName, costPoints)));
            return toJob(record);
        } catch (IOException exception) {
            deleteQuietly(temporaryInput);
            if (canonicalInputCreated) deleteQuietly(inputPath);
            throw new IllegalStateException("平台适配文档上传保存失败：" + compact(exception.getMessage()), exception);
        } catch (RuntimeException exception) {
            deleteQuietly(temporaryInput);
            if (canonicalInputCreated) deleteQuietly(inputPath);
            throw exception;
        }
    }

    private void process(String jobId, Path inputPath, XuejieRewriteMode mode,
                         XuejiePlatform platform, String featureCode,
                         String featureName, int costPoints) {
        try {
            docxValidator.validateStagedFile(inputPath);
            stateRepository.stage(jobId, XuejieExternalJobStateRepository.CONFIGURING, null,
                    "doubao:" + platform.name());
            update(jobId, "RUNNING", null, 0, 0, true,
                    "正在加载 " + platform.remoteName() + " 平台 Skill（本机豆包）");
            stateRepository.stage(jobId, XuejieExternalJobStateRepository.PROCESSING, null,
                    "doubao:" + platform.name());

            Path output = resultPath(jobId);
            PlatformDoubaoDocumentProcessor.ProcessingResult result = processor.process(
                    inputPath, output, platform, mode,
                    (total, processed, rewritten, message) ->
                            update(jobId, "RUNNING", total, processed, rewritten, true, message));
            docxValidator.validateStagedFile(output);
            finalizeSuccessfulJob(jobId, platform.remoteName(), result);
        } catch (Exception exception) {
            deleteQuietly(resultPath(jobId));
            boolean refunded = refund(jobId, featureCode, featureName, costPoints,
                    "本机豆包平台适配处理失败");
            update(jobId, "FAILED", null, 0, 0, !refunded,
                    "平台适配处理失败：" + compact(exception.getMessage())
                            + (refunded ? "；DropAI 预扣积分已退回" : ""));
            stateRepository.stage(jobId, XuejieExternalJobStateRepository.FAILED,
                    null, exception.getClass().getSimpleName());
            log.warn("Platform Doubao job failed jobId={} platform={} type={} message={}",
                    jobId, platform.name(), exception.getClass().getSimpleName(),
                    compact(exception.getMessage()), exception);
        } finally {
            deleteQuietly(inputPath);
        }
    }

    public void recover(XuejieExternalJobStateRepository.State state) {
        XuejiePlatform platform = XuejiePlatform.require(state.platform());
        XuejieRewriteMode mode = XuejieRewriteMode.require(state.mode());
        Path inputPath = uploadDir.resolve(state.jobId() + "-platform-source.docx");
        Path legacyInputPath = uploadDir.resolve(state.jobId() + "-external-source.docx");
        if (!Files.isRegularFile(inputPath) && Files.isRegularFile(legacyInputPath)) inputPath = legacyInputPath;
        if (recoverAlreadyFinalizedSuccess(state, inputPath)) return;
        try {
            docxValidator.validateStagedFile(inputPath);
        } catch (RuntimeException invalidSource) {
            boolean refunded = refund(state.jobId(), state.featureCode(), state.featureName(),
                    state.costPoints(), "服务重启恢复时本地源 DOCX 缺失或无效");
            update(state.jobId(), "FAILED", null, 0, 0, !refunded,
                    "平台适配任务未恢复：本地源 DOCX 缺失或无效"
                            + (refunded ? "，DropAI 预扣积分已退回" : ""));
            stateRepository.stage(state.jobId(), XuejieExternalJobStateRepository.FAILED,
                    null, "invalid_staged_source");
            return;
        }
        Path recoveredInput = inputPath;
        update(state.jobId(), "RUNNING", null, 0, 0, true,
                "服务重启后正在恢复本机豆包平台适配任务");
        executor.submit(() -> process(state.jobId(), recoveredInput, mode, platform,
                state.featureCode(), state.featureName(), state.costPoints()));
    }

    void finalizeSuccessfulJob(String jobId, String platformName,
                               PlatformDoubaoDocumentProcessor.ProcessingResult result) {
        int unchanged = Math.max(0, result.processedParagraphs()
                - result.rewrittenParagraphs() - result.failedParagraphs());
        StringBuilder warning = new StringBuilder();
        if (unchanged > 0) {
            warning.append("；").append(unchanged).append(" 个模型未实质改写的段落已保留原文");
        }
        if (result.failedParagraphs() > 0) {
            warning.append("；").append(result.failedParagraphs())
                    .append(" 个未通过保护校验的段落已保留原文");
        }
        update(jobId, "SUCCESS", result.totalParagraphs(), result.processedParagraphs(),
                result.rewrittenParagraphs(), true,
                platformName + " Skill 适配完成，结果文件已生成" + warning);
        stateRepository.stage(jobId, XuejieExternalJobStateRepository.COMPLETED,
                null, "doubao_completed");
    }

    /** Kept as a small compatibility seam for the existing terminal-ordering regression test. */
    void finalizeSuccessfulJob(String jobId, String platformName,
                               String ignoredTaskId, String ignoredStatus) {
        finalizeSuccessfulJob(jobId, platformName,
                new PlatformDoubaoDocumentProcessor.ProcessingResult(1, 1, 1, 0, java.util.List.of()));
    }

    private boolean recoverAlreadyFinalizedSuccess(XuejieExternalJobStateRepository.State state,
                                                    Path inputPath) {
        DocumentJobRecord record = documentJobMapper.selectById(state.jobId());
        if (record == null || !"SUCCESS".equals(record.getStatus()) || !hasSavedResult(state.jobId())) {
            return false;
        }
        stateRepository.stage(state.jobId(), XuejieExternalJobStateRepository.COMPLETED,
                null, "doubao_completed");
        deleteQuietly(inputPath);
        return true;
    }

    private boolean refund(String jobId, String featureCode, String featureName, int points, String reason) {
        DocumentJobRecord record = documentJobMapper.selectById(jobId);
        if (record == null || !Boolean.TRUE.equals(record.getPointsCharged())) return false;
        return refundService.refundIfNeeded(record.getUserId(), jobId, featureCode, featureName, points, reason);
    }

    private Path resultPath(String jobId) {
        return outputDir.resolve(jobId + "-ai-optimized.docx");
    }

    private boolean hasSavedResult(String jobId) {
        try {
            Path result = resultPath(jobId);
            return Files.isRegularFile(result) && Files.size(result) > 0;
        } catch (IOException ignored) {
            return false;
        }
    }

    private void moveStagedInput(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void update(String jobId, String status, Integer total, int processed, int rewritten,
                        boolean pointsCharged, String message) {
        DocumentJobRecord record = documentJobMapper.selectById(jobId);
        if (record == null) return;
        record.setStatus(status);
        if (total != null) record.setTotalParagraphs(total);
        record.setProcessedParagraphs(processed);
        record.setRewrittenParagraphs(rewritten);
        record.setPointsCharged(pointsCharged && value(record.getCostPoints()) > 0);
        record.setMessage(message);
        record.setUpdatedAt(LocalDateTime.now());
        documentJobMapper.updateById(record);
    }

    private DocumentJobRecord initialRecord(String jobId, Long userId, String originalName,
                                            XuejieRewriteMode mode, XuejiePlatform platform,
                                            int charCount, int costPoints) {
        LocalDateTime now = LocalDateTime.now();
        DocumentJobRecord record = new DocumentJobRecord();
        record.setJobId(jobId);
        record.setUserId(userId);
        record.setFileName(originalName);
        record.setSourceFeature(SOURCE_FEATURE);
        record.setMode(mode.apiValue());
        record.setModeName(mode.displayName());
        record.setPlatform(platform.name());
        record.setPlatformName(platform.remoteName());
        record.setStatus("PENDING");
        record.setTotalParagraphs(0);
        record.setProcessedParagraphs(0);
        record.setRewrittenParagraphs(0);
        record.setCharCount(charCount);
        record.setCostPoints(costPoints);
        record.setPointsCharged(costPoints > 0);
        record.setMessage("文档已上传，等待本机豆包执行 " + platform.remoteName() + " 平台 Skill");
        record.setParagraphsJson("[]");
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    private DocumentRewriteJobVO existingJob(DocumentJobRecord record, Long userId,
                                              XuejieRewriteMode mode, XuejiePlatform platform) {
        if (!userId.equals(record.getUserId())) throw new IllegalStateException("请求 ID 冲突");
        if (!mode.apiValue().equals(record.getMode()) || !platform.name().equals(record.getPlatform())) {
            throw new IllegalStateException("同一 requestId 不能更换平台或模式");
        }
        return toJob(record);
    }

    private DocumentRewriteJobVO toJob(DocumentJobRecord record) {
        DocumentRewriteJobVO job = new DocumentRewriteJobVO();
        job.setJobId(record.getJobId());
        job.setFileName(record.getFileName());
        job.setSourceFeature(record.getSourceFeature());
        job.setMode(record.getMode());
        job.setModeName(record.getModeName());
        job.setPlatform(record.getPlatform());
        job.setPlatformName(record.getPlatformName());
        job.setStatus(record.getStatus());
        job.setTotalParagraphs(value(record.getTotalParagraphs()));
        job.setProcessedParagraphs(value(record.getProcessedParagraphs()));
        job.setRewrittenParagraphs(value(record.getRewrittenParagraphs()));
        job.setCharCount(value(record.getCharCount()));
        job.setCostPoints(value(record.getCostPoints()));
        job.setPointsCharged(Boolean.TRUE.equals(record.getPointsCharged()));
        job.setMessage(record.getMessage());
        if ("SUCCESS".equals(record.getStatus())) {
            job.setDownloadUrl("/api/document/rewrite/download/" + record.getJobId());
        }
        job.setCreatedAt(record.getCreatedAt());
        job.setUpdatedAt(record.getUpdatedAt());
        return job;
    }

    private String featureCode(XuejieRewriteMode mode) {
        return mode == XuejieRewriteMode.DOUBLE ? PointService.DOCUMENT_DOUBLE : PointService.DOCUMENT_HUMANIZE;
    }

    private String featureName(XuejieRewriteMode mode) {
        return mode == XuejieRewriteMode.DOUBLE ? "文档双降" : "文档降AI";
    }

    private String jobId(Long userId, String requestId) {
        if (requestId == null || requestId.isBlank()) return UUID.randomUUID().toString().replace("-", "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(("platform-doubao:" + userId + ":" + requestId.trim())
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成平台适配任务 ID", exception);
        }
    }

    private String safeOriginalName(String name) {
        String normalized = name == null || name.isBlank() ? "document.docx" : name.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        if (separator >= 0) normalized = normalized.substring(separator + 1);
        normalized = normalized.replaceAll("[\\x00-\\x1F\\x7F]", "_").trim();
        if (normalized.length() > 240) {
            String extension = normalized.toLowerCase().endsWith(".docx") ? ".docx" : "";
            normalized = normalized.substring(0, 240 - extension.length()) + extension;
        }
        return normalized.isBlank() ? "document.docx" : normalized;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String compact(String message) {
        if (message == null || message.isBlank()) return "无详细信息";
        String value = message.replaceAll("\\s+", " ").trim();
        return value.length() > 180 ? value.substring(0, 180) + "..." : value;
    }

    private void scheduleAfterCommit(Runnable task) {
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

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "platform-doubao-document-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}
