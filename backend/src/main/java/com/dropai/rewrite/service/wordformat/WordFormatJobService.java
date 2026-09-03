package com.dropai.rewrite.service.wordformat;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.config.WordFormatProperties;
import com.dropai.rewrite.vo.WordFormatJobVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class WordFormatJobService {
    private static final Logger log = LoggerFactory.getLogger(WordFormatJobService.class);
    private static final Set<String> TEMPLATE_EXTENSIONS = Set.of("doc", "docx", "dotx");
    private static final byte[] OLE_SIGNATURE = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };
    private static final int MAX_ZIP_ENTRIES = 20_000;

    private final WordFormatProperties properties;
    private final WordFormatProcessRunner runner;
    private final Path dataRoot;
    private final ThreadPoolExecutor executor;
    private final Semaphore taskSlots;
    private final Map<String, JobState> jobs = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Autowired
    public WordFormatJobService(WordFormatProperties properties, WordFormatProcessRunner runner, ObjectMapper objectMapper) {
        this.properties = properties;
        this.runner = runner;
        this.objectMapper = objectMapper;
        this.dataRoot = properties.dataDir();
        this.taskSlots = new Semaphore(properties.maxConcurrent() + properties.queueCapacity(), true);
        this.executor = new ThreadPoolExecutor(
                properties.maxConcurrent(),
                properties.maxConcurrent(),
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(properties.queueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable, "word-format-job");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public WordFormatJobService(WordFormatProperties properties, WordFormatProcessRunner runner) {
        this(properties, runner, new ObjectMapper());
    }

    public WordFormatJobVO submit(
            MultipartFile template,
            MultipartFile source,
            String instructions,
            boolean useDoubao
    ) {
        if (!properties.enabled()) {
            throw new IllegalStateException("Word 格式处理功能当前未启用");
        }
        long userId = AuthContext.requireUserId();
        Upload templateUpload = validateUpload(template, true);
        Upload sourceUpload = validateUpload(source, false);
        String normalizedInstructions = instructions == null ? "" : instructions.trim();
        boolean effectiveUseDoubao = true;
        if (normalizedInstructions.length() > properties.maxInstructionsChars()) {
            throw new IllegalArgumentException("补充要求不能超过 " + properties.maxInstructionsChars() + " 个字符");
        }
        String jobId = UUID.randomUUID().toString().replace("-", "");
        Path jobDir = inside(dataRoot.resolve(String.valueOf(userId)).resolve(jobId));
        Path sourcePath = inside(jobDir.resolve("source.docx"));
        Path templatePath = inside(jobDir.resolve("template." + templateUpload.extension()));
        Path outputPath = inside(jobDir.resolve("formatted.docx"));
        Path resultPath = inside(jobDir.resolve("result.json"));
        Path rulesPath = inside(jobDir.resolve("confirmed-rules.json"));
        Path instructionsPath = normalizedInstructions.isBlank()
                ? null
                : inside(jobDir.resolve("instructions.txt"));

        if (!taskSlots.tryAcquire()) {
            throw new JobQueueFullException("格式处理任务较多，请等待已有任务完成后重试");
        }
        boolean slotTransferred = false;

        try {
            verifyRuntimeBeforeAccepting(templateUpload, effectiveUseDoubao);
            try {
                Files.createDirectories(jobDir);
                copyUpload(source, sourcePath);
                copyUpload(template, templatePath);
                validateStoredFile(sourcePath, "docx", false);
                validateStoredFile(templatePath, templateUpload.extension(), true);
                if (instructionsPath != null) {
                    Files.writeString(instructionsPath, normalizedInstructions, StandardCharsets.UTF_8);
                }
            } catch (Exception exception) {
                cleanupRejectedJob(jobDir, sourcePath, templatePath, instructionsPath);
                if (exception instanceof IllegalArgumentException illegalArgumentException) {
                    throw illegalArgumentException;
                }
                log.error(
                        "Unable to persist Word formatter uploads: jobId={}, userId={}, jobDirectory={}",
                        jobId, userId, jobDir, exception
                );
                throw new IllegalStateException("上传文件保存失败，请重试");
            }

            String outputName = outputName(sourceUpload.displayName());
            JobState job = new JobState(
                    jobId,
                    userId,
                    sourceUpload.displayName(),
                    templateUpload.displayName(),
                    outputName,
                    effectiveUseDoubao,
                    jobDir,
                    sourcePath,
                    templatePath,
                    outputPath,
                    resultPath,
                    instructionsPath,
                    rulesPath
            );
            jobs.put(jobId, job);
            try {
                executor.execute(() -> {
                    try {
                        analyze(job);
                    } finally {
                        taskSlots.release();
                    }
                });
                slotTransferred = true;
            } catch (RejectedExecutionException exception) {
                jobs.remove(jobId, job);
                cleanupRejectedJob(jobDir, sourcePath, templatePath, instructionsPath, outputPath, resultPath);
                throw new JobQueueFullException("格式处理服务正在停止，请稍后重试");
            }
            return job.view();
        } finally {
            if (!slotTransferred) {
                taskSlots.release();
            }
        }
    }

    public WordFormatJobVO get(String jobId) {
        return ownedJob(jobId, AuthContext.requireUserId()).view();
    }

    public WordFormatJobVO confirm(String jobId, Map<String, Object> editableRules) {
        JobState job = ownedJob(jobId, AuthContext.requireUserId());
        synchronized (job) {
            if (!"AWAITING_CONFIRMATION".equals(job.status)) {
                throw new JobNotReadyException("任务尚未完成 AI 分析或已经确认");
            }
            if (editableRules == null || editableRules.isEmpty()) {
                throw new IllegalArgumentException("请提交需要确认的三类格式规则");
            }
            try {
                byte[] json = objectMapper.writeValueAsBytes(editableRules);
                if (json.length > 128 * 1024) throw new IllegalArgumentException("确认规则内容过大");
                Files.write(job.rulesPath, json);
            } catch (IOException exception) {
                throw new IllegalStateException("无法保存确认规则，请重试", exception);
            }
            if (!taskSlots.tryAcquire()) throw new JobQueueFullException("格式处理任务较多，请稍后重试");
            job.running(45, "confirmed", "格式规则已确认，准备正式处理论文");
            try {
                executor.execute(() -> { try { process(job); } finally { taskSlots.release(); } });
            } catch (RejectedExecutionException exception) {
                taskSlots.release();
                job.awaiting();
                throw new JobQueueFullException("格式处理服务正在停止，请稍后重试");
            }
            return job.view();
        }
    }

    public DownloadFile download(String jobId) {
        JobState job = ownedJob(jobId, AuthContext.requireUserId());
        synchronized (job) {
            if (!"SUCCESS".equals(job.status)) {
                throw new JobNotReadyException("格式处理尚未完成");
            }
            Path output = inside(job.outputPath);
            try {
                if (!Files.isRegularFile(output) || Files.size(output) == 0) {
                    throw new JobNotReadyException("格式处理结果不存在");
                }
                return new DownloadFile(new FileSystemResource(output), job.outputFileName, Files.size(output));
            } catch (IOException exception) {
                throw new IllegalStateException("无法读取格式处理结果", exception);
            }
        }
    }

    private void analyze(JobState job) {
        job.running(12, "extracting_template", "正在提取模板并进行 AI 分析");
        try {
            WordFormatProcessRunner.ProcessResult result = runner.run(
                    job.sourcePath, job.templatePath, job.outputPath, job.resultPath,
                    job.instructionsPath, true, true, null,
                    event -> job.progress(event.progress(), event.stage(), event.message())
            );
            job.analysisReady(result.editableRules(), result.lockedRules(), result.analysis(), result.templateNotes());
        } catch (Exception exception) {
            log.error("Word formatter analysis failed: jobId={}", job.jobId, exception);
            job.fail(userFacingWorkerError(exception));
        }
    }

    private void process(JobState job) {
        job.running(12, "starting", "已读取上传文件，准备提取学校模板格式");
        try {
            WordFormatProcessRunner.ProcessResult result = runner.run(
                    job.sourcePath,
                    job.templatePath,
                    job.outputPath,
                    job.resultPath,
                    job.instructionsPath,
                    job.useDoubao,
                    false,
                    job.rulesPath,
                    event -> job.progress(event.progress(), event.stage(), event.message())
            );
            job.running(96, "integrity_check", "格式修改已完成，正在检查 DOCX 完整性");
            validateStoredFile(job.outputPath, "docx", false);
            job.success(Math.max(0, result.changedCount()), result.warnings(), result.templateNotes());
        } catch (Exception exception) {
            cleanupFailedArtifacts(job);
            log.error(
                    "Word formatter job failed: jobId={}, userId={}, jobDirectory={}",
                    job.jobId, job.userId, job.jobDir, exception
            );
            job.fail(userFacingWorkerError(exception));
        }
    }

    private void verifyRuntimeBeforeAccepting(Upload templateUpload, boolean requireDoubao) {
        boolean legacyTemplate = Set.of("doc", "dotx").contains(templateUpload.extension());
        try {
            runner.verifyRuntime(legacyTemplate, requireDoubao);
        } catch (Exception exception) {
            log.error(
                    "Word formatter runtime preflight rejected a submission: python={}, worker={}, legacy={}, doubao={}",
                    properties.python(), properties.worker(), legacyTemplate, requireDoubao, exception
            );
            throw new IllegalStateException(WordFormatProcessRunner.RUNTIME_UNAVAILABLE_MESSAGE);
        }
    }

    private void cleanupFailedArtifacts(JobState job) {
        Path finalLog = job.outputPath.resolveSibling("formatted.log.json");
        for (Path file : List.of(job.outputPath, finalLog)) {
            try {
                Files.deleteIfExists(inside(file));
            } catch (IOException ignored) {
                // A failed task never exposes these artifacts for download.
            }
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(job.jobDir)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (name.startsWith(".")
                        && (name.endsWith(".working.docx") || name.endsWith(".working.log.json"))) {
                    try {
                        Files.deleteIfExists(inside(file));
                    } catch (IOException ignored) {
                        // Best effort after a force-terminated worker releases its handles.
                    }
                }
            }
        } catch (IOException ignored) {
            // Preserve the worker error as the task's user-facing failure.
        }
    }

    private Upload validateUpload(MultipartFile file, boolean template) {
        String label = template ? "学校模板" : "论文原稿";
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new IllegalArgumentException("请选择有效的" + label + "文件");
        }
        long limit = template ? properties.maxTemplateBytes() : properties.maxSourceBytes();
        if (file.getSize() > limit) {
            throw new IllegalArgumentException(label + "不能超过 " + humanSize(limit));
        }
        String displayName = cleanDisplayName(file.getOriginalFilename(), template ? "template.docx" : "source.docx");
        String extension = extension(displayName);
        if (template && !TEMPLATE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("学校模板仅支持 .doc、.docx 或 .dotx 文件");
        }
        if (template && Set.of("doc", "dotx").contains(extension) && !properties.legacyTemplatesEnabled()) {
            throw new IllegalArgumentException("当前服务器不支持旧版 .doc/.dotx 模板，请在 Microsoft Word 中另存为 .docx 后上传");
        }
        if (!template && !"docx".equals(extension)) {
            throw new IllegalArgumentException("论文原稿当前仅支持 .docx 文件");
        }
        return new Upload(displayName, extension);
    }

    private static void copyUpload(MultipartFile file, Path target) throws IOException {
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void validateStoredFile(Path path, String extension, boolean template) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) == 0) {
                throw new IllegalArgumentException((template ? "学校模板" : "论文文件") + "为空");
            }
            byte[] header = readHeader(path, 8);
            if ("doc".equals(extension)) {
                if (!startsWith(header, OLE_SIGNATURE)) {
                    throw new IllegalArgumentException("上传的 .doc 模板不是有效的旧版 Word 文件");
                }
                return;
            }
            if (!isZipHeader(header)) {
                throw new IllegalArgumentException("上传文件不是有效的 DOCX/DOTX 文档");
            }
            validateOpenXml(path);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Unable to validate uploaded Word file: path={}, template={}", path, template, exception);
            throw new IllegalArgumentException("无法读取上传的 Word 文件，请确认文件完整后重试");
        }
    }

    private void validateOpenXml(Path path) throws IOException {
        long expanded = 0;
        int entries = 0;
        boolean contentTypes = false;
        boolean documentXml = false;
        try (ZipFile zip = new ZipFile(path.toFile())) {
            var iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                ZipEntry entry = iterator.nextElement();
                entries++;
                if (entries > MAX_ZIP_ENTRIES) {
                    throw new IllegalArgumentException("Word 文件包含过多压缩条目");
                }
                String name = entry.getName();
                if ("[Content_Types].xml".equals(name)) {
                    contentTypes = true;
                } else if ("word/document.xml".equals(name)) {
                    documentXml = true;
                }
                long size = entry.getSize();
                long compressed = entry.getCompressedSize();
                if (size > 0) {
                    expanded = Math.addExact(expanded, size);
                    if (expanded > properties.maxExpandedBytes()) {
                        throw new IllegalArgumentException("Word 文件解压后的内容过大");
                    }
                    if (size > 1_000_000 && compressed > 0 && (double) compressed / size < 0.001d) {
                        throw new IllegalArgumentException("Word 文件压缩比例异常");
                    }
                }
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Word 文件解压后的内容过大", exception);
        }
        if (!contentTypes || !documentXml) {
            throw new IllegalArgumentException("压缩包不是有效的 Word 文档");
        }
    }

    private JobState ownedJob(String jobId, long userId) {
        if (jobId == null || !jobId.matches("[0-9a-fA-F]{32}")) {
            throw new JobNotFoundException("格式处理任务不存在");
        }
        JobState job = jobs.get(jobId);
        if (job == null || job.userId != userId) {
            throw new JobNotFoundException("格式处理任务不存在或无权访问");
        }
        return job;
    }

    private Path inside(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(dataRoot)) {
            throw new IllegalArgumentException("非法的格式处理文件路径");
        }
        return normalized;
    }

    private void cleanupRejectedJob(Path directory, Path... files) {
        for (Path file : files) {
            if (file == null) {
                continue;
            }
            try {
                Files.deleteIfExists(inside(file));
            } catch (IOException ignored) {
                // The rejected job remains inaccessible and can be removed by normal storage cleanup.
            }
        }
        try {
            Files.deleteIfExists(inside(directory));
            Path userDirectory = directory.getParent();
            if (userDirectory != null) {
                Files.deleteIfExists(inside(userDirectory));
            }
        } catch (IOException ignored) {
            // Keep validation failure as the user-facing error.
        }
    }

    private static String userFacingWorkerError(Exception exception) {
        if (exception instanceof WordFormatProcessRunner.RuntimeUnavailableException) {
            return WordFormatProcessRunner.RUNTIME_UNAVAILABLE_MESSAGE;
        }
        return WordFormatProcessRunner.PROCESS_FAILED_MESSAGE;
    }

    private static byte[] readHeader(Path path, int length) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return input.readNBytes(length);
        }
    }

    private static boolean startsWith(byte[] source, byte[] prefix) {
        if (source.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (source[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isZipHeader(byte[] header) {
        return header.length >= 4
                && header[0] == 'P'
                && header[1] == 'K'
                && ((header[2] == 3 && header[3] == 4)
                || (header[2] == 5 && header[3] == 6)
                || (header[2] == 7 && header[3] == 8));
    }

    private static String cleanDisplayName(String original, String fallback) {
        String value = original == null ? "" : original.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        value = value.replaceAll("[\\p{Cntrl}]", "_").trim();
        if (value.isBlank()) {
            value = fallback;
        }
        return value.length() <= 240 ? value : value.substring(value.length() - 240);
    }

    private static String outputName(String sourceName) {
        String base = sourceName.replaceFirst("(?i)\\.docx$", "");
        base = base.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        if (base.isBlank()) {
            base = "论文";
        }
        if (base.length() > 160) {
            base = base.substring(0, 160);
        }
        return base + "-格式修订版.docx";
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String humanSize(long bytes) {
        long megabytes = Math.max(1, bytes / (1024 * 1024));
        return megabytes + "MB";
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public record DownloadFile(FileSystemResource resource, String fileName, long size) {
    }

    private record Upload(String displayName, String extension) {
    }

    public static class JobNotFoundException extends RuntimeException {
        public JobNotFoundException(String message) {
            super(message);
        }
    }

    public static class JobNotReadyException extends RuntimeException {
        public JobNotReadyException(String message) {
            super(message);
        }
    }

    public static class JobQueueFullException extends RuntimeException {
        public JobQueueFullException(String message) {
            super(message);
        }
    }

    private static final class JobState {
        private final String jobId;
        private final long userId;
        private final String sourceFileName;
        private final String templateFileName;
        private final String outputFileName;
        private final boolean useDoubao;
        private final Path jobDir;
        private final Path sourcePath;
        private final Path templatePath;
        private final Path outputPath;
        private final Path resultPath;
        private final Path instructionsPath;
        private final Path rulesPath;
        private final LocalDateTime createdAt = LocalDateTime.now();
        private String status = "QUEUED";
        private int progress = 8;
        private String currentStage = "queued";
        private String message = "任务已进入格式处理队列";
        private int changedCount;
        private List<String> warnings = List.of();
        private List<String> templateNotes = List.of();
        private Map<String, Object> editableRules = Map.of();
        private List<String> lockedRules = List.of();
        private Map<String, Object> analysis = Map.of();
        private LocalDateTime updatedAt = createdAt;

        private JobState(
                String jobId,
                long userId,
                String sourceFileName,
                String templateFileName,
                String outputFileName,
                boolean useDoubao,
                Path jobDir,
                Path sourcePath,
                Path templatePath,
                Path outputPath,
                Path resultPath,
                Path instructionsPath,
                Path rulesPath
        ) {
            this.jobId = jobId;
            this.userId = userId;
            this.sourceFileName = sourceFileName;
            this.templateFileName = templateFileName;
            this.outputFileName = outputFileName;
            this.useDoubao = useDoubao;
            this.jobDir = jobDir;
            this.sourcePath = sourcePath;
            this.templatePath = templatePath;
            this.outputPath = outputPath;
            this.resultPath = resultPath;
            this.instructionsPath = instructionsPath;
            this.rulesPath = rulesPath;
        }

        private synchronized void running(int value, String stage, String detail) {
            status = "RUNNING";
            progress = Math.max(progress, Math.min(99, Math.max(0, value)));
            if (stage != null && !stage.isBlank()) {
                currentStage = stage;
            }
            if (detail != null && !detail.isBlank()) {
                message = detail;
            }
            updatedAt = LocalDateTime.now();
        }

        private synchronized void progress(int value, String stage, String detail) {
            int normalized = value < 0 ? progress : Math.max(12, Math.min(95, value));
            running(normalized, stage, detail);
        }

        private synchronized void success(int changes, List<String> warnings, List<String> notes) {
            status = "SUCCESS";
            progress = 100;
            currentStage = "completed";
            changedCount = changes;
            this.warnings = immutable(warnings);
            this.templateNotes = immutable(notes);
            message = this.warnings.isEmpty()
                    ? "论文已按学校模板完成格式修改"
                    : "格式修改完成，请查看处理提示";
            updatedAt = LocalDateTime.now();
        }

        private synchronized void analysisReady(Map<String, Object> editable, List<String> locked,
                                                Map<String, Object> analysis, List<String> notes) {
            status = "AWAITING_CONFIRMATION";
            progress = 40;
            currentStage = "awaiting_confirmation";
            message = "AI 分析完成，请核对并确认三类可编辑格式";
            editableRules = editable == null ? Map.of() : Map.copyOf(editable);
            lockedRules = immutable(locked);
            this.analysis = analysis == null ? Map.of() : Map.copyOf(analysis);
            templateNotes = immutable(notes);
            updatedAt = LocalDateTime.now();
        }

        private synchronized void awaiting() {
            status = "AWAITING_CONFIRMATION";
            currentStage = "awaiting_confirmation";
            message = "请再次确认格式规则";
            updatedAt = LocalDateTime.now();
        }

        private synchronized void fail(String reason) {
            status = "FAILED";
            currentStage = "failed";
            message = reason == null || reason.isBlank() ? "格式处理失败，请重试" : reason;
            updatedAt = LocalDateTime.now();
        }

        private synchronized WordFormatJobVO view() {
            Map<String, Object> result = ("SUCCESS".equals(status) || "AWAITING_CONFIRMATION".equals(status))
                    ? Map.of(
                    "changedCount", changedCount,
                    "warnings", warnings,
                    "templateNotes", templateNotes,
                    "summary", message,
                    "editableRules", editableRules,
                    "lockedRules", lockedRules,
                    "analysis", analysis
            )
                    : Map.of();
            return new WordFormatJobVO(
                    jobId,
                    status,
                    progress,
                    currentStage,
                    message,
                    sourceFileName,
                    templateFileName,
                    outputFileName,
                    useDoubao,
                    changedCount,
                    warnings,
                    templateNotes,
                    result,
                    "SUCCESS".equals(status) ? "/api/word-format/jobs/" + jobId + "/download" : null,
                    createdAt,
                    updatedAt
            );
        }

        private static List<String> immutable(List<String> values) {
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            return List.copyOf(new ArrayList<>(values));
        }
    }
}
