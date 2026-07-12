package com.dropai.rewrite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.ComputerGeneratedFile;
import com.dropai.rewrite.entity.ComputerGenerationJob;
import com.dropai.rewrite.entity.ComputerPreviewInstance;
import com.dropai.rewrite.mapper.ComputerGeneratedFileMapper;
import com.dropai.rewrite.mapper.ComputerGenerationJobMapper;
import com.dropai.rewrite.mapper.ComputerPreviewInstanceMapper;
import com.dropai.rewrite.modules.documentParser.DocumentParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ComputerGeneratorService {
    private static final String OUT_OF_SCOPE_MESSAGE = "该需求超出毕业设计与教学项目范围，仅支持合法合规的软件工程项目生成。";
    private static final List<String> FORBIDDEN_REQUIREMENT_KEYWORDS = List.of(
            "漏洞利用", "爆破", "撞库", "扫描工具", "端口扫描", "网络攻击", "攻击工具", "渗透",
            "绕过认证", "绕过登录", "绕过访问控制", "验证码绕过", "绕过验证码",
            "自动化账号注册", "代理池", "未授权数据采集", "未授权接口", "非法监控",
            "恶意自动化", "木马", "后门", "shellcode", "exploit", "bruteforce", "credential stuffing",
            "port scan", "bypass authentication", "bypass captcha", "proxy pool", "malware"
    );
    private static final List<String> STAGES = List.of(
            "项目识别", "目录生成", "SQL生成", "后端生成", "前端生成",
            "论文生成", "预览构建", "ZIP打包", "生成完成"
    );
    private static final Path ROOT = Paths.get("work", "computer-generator").toAbsolutePath().normalize();

    private final ComputerGenerationJobMapper jobMapper;
    private final ComputerGeneratedFileMapper fileMapper;
    private final ComputerPreviewInstanceMapper previewMapper;
    private final DocumentParser documentParser;
    private final PointService pointService;
    private final MatrixDesignService matrixDesignService;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;

    public ComputerGeneratorService(ComputerGenerationJobMapper jobMapper, ComputerGeneratedFileMapper fileMapper,
                                    ComputerPreviewInstanceMapper previewMapper, DocumentParser documentParser,
                                    PointService pointService, MatrixDesignService matrixDesignService,
                                    ObjectMapper objectMapper, TaskExecutor taskExecutor) {
        this.jobMapper = jobMapper;
        this.fileMapper = fileMapper;
        this.previewMapper = previewMapper;
        this.documentParser = documentParser;
        this.pointService = pointService;
        this.matrixDesignService = matrixDesignService;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
    }

    @Transactional
    public ComputerAnalyzeVO analyze(List<MultipartFile> files) {
        Long userId = AuthContext.requireUserId();
        List<MultipartFile> safeFiles = files == null ? List.of() : files;
        if (safeFiles.isEmpty()) throw new IllegalArgumentException("请先上传任务书或开题报告");
        List<DocumentParser.ParsedDocument> parsed = documentParser.parse(safeFiles, safeFiles.stream().map(file -> "TASK_BOOK").toList());
        StringBuilder input = new StringBuilder();
        List<String> names = new ArrayList<>();
        for (DocumentParser.ParsedDocument doc : parsed) {
            names.add(doc.fileName());
            if (doc.textReadable()) {
                input.append("\n\n【").append(doc.fileName()).append("】\n").append(doc.text());
            }
        }
        if (input.isEmpty()) {
            input.append("用户上传了计算机毕业设计资料，但未读取到完整文字。请根据文件名和常见毕业设计要求主动补全。");
        }
        ensureCompliantRequirement(input.toString());
        String seedTitle = extractTitle(input.toString(), names);
        ComputerGenerationJob temp = new ComputerGenerationJob();
        temp.setId("preview");
        temp.setUserId(userId);
        temp.setTitle(seedTitle);
        temp.setProjectType("自动识别");
        temp.setTechStack("自动推荐");
        temp.setInputText(input.toString());
        ComputerProjectPlan plan = generatePlanWithMatrix(temp);
        if (plan == null) {
            plan = analyze(temp);
        }
        String projectType = recommendProjectType(plan, input.toString());
        String techStack = recommendTechStack(projectType, input.toString());
        int cost = estimateCost(projectType, true, true);
        pointService.ensureEnoughCustom(userId, cost);

        LocalDateTime now = LocalDateTime.now();
        ComputerGenerationJob job = new ComputerGenerationJob();
        job.setId("cg_" + UUID.randomUUID().toString().replace("-", ""));
        job.setUserId(userId);
        job.setTitle(plan.title());
        job.setProjectType(projectType);
        job.setTechStack(techStack);
        job.setStatus("ANALYZED");
        job.setProgress(0);
        job.setCurrentStage("智能识别完成");
        job.setCurrentFile("");
        job.setInputText(input.toString().trim());
        job.setUploadedFiles(String.join(",", names));
        job.setPointsCost(cost);
        job.setPointsCharged(false);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        jobMapper.insert(job);
        return new ComputerAnalyzeVO(toVO(job, List.of(), null), toPlanVO(plan, projectType, techStack, cost));
    }

    @Transactional
    public ComputerJobVO create(CreateComputerJobRequest request) {
        Long userId = AuthContext.requireUserId();
        String title = clean(request.title());
        if (title.isBlank()) throw new IllegalArgumentException("请填写项目题目");
        String input = clean(request.inputText());
        int cost = estimateCost(request.projectType(), request.generatePaper(), request.enablePreview());
        pointService.ensureEnoughCustom(userId, cost);

        LocalDateTime now = LocalDateTime.now();
        ComputerGenerationJob job = new ComputerGenerationJob();
        job.setId("cg_" + UUID.randomUUID().toString().replace("-", ""));
        job.setUserId(userId);
        job.setTitle(title);
        job.setProjectType(defaultText(request.projectType(), "Java Web 项目"));
        job.setTechStack(defaultText(request.techStack(), "Spring Boot + Vue + MySQL"));
        job.setStatus("PENDING");
        job.setProgress(0);
        job.setCurrentStage("等待开始");
        job.setInputText(input);
        job.setUploadedFiles("[]");
        job.setPointsCost(cost);
        job.setPointsCharged(false);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        jobMapper.insert(job);
        return toVO(job, List.of(), previewMapper.selectOne(new LambdaQueryWrapper<ComputerPreviewInstance>().eq(ComputerPreviewInstance::getJobId, job.getId())));
    }

    @Transactional
    public ComputerJobVO upload(String jobId, List<MultipartFile> files) {
        ComputerGenerationJob job = requireOwnedJob(jobId);
        List<MultipartFile> safeFiles = files == null ? List.of() : files;
        List<DocumentParser.ParsedDocument> parsed = documentParser.parse(safeFiles, safeFiles.stream().map(file -> "TASK_BOOK").toList());
        StringBuilder input = new StringBuilder(defaultText(job.getInputText(), ""));
        List<String> names = new ArrayList<>();
        for (DocumentParser.ParsedDocument doc : parsed) {
            names.add(doc.fileName());
            if (doc.textReadable()) {
                input.append("\n\n【").append(doc.fileName()).append("】\n").append(doc.text());
            }
        }
        job.setInputText(input.toString().trim());
        job.setUploadedFiles(String.join(",", names));
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateById(job);
        return status(jobId);
    }

    @Transactional
    public ComputerJobVO start(String jobId, ComputerGenerationConfig config) {
        ComputerGenerationJob job = requireOwnedJob(jobId);
        if ("SUCCESS".equals(job.getStatus())) return result(jobId);
        if ("RUNNING".equals(job.getStatus())) return status(jobId);
        if (config != null) {
            ensureCompliantRequirement(configToText(config));
            applyConfig(job, config);
        } else {
            ensureCompliantRequirement(defaultText(job.getInputText(), "") + "\n" + defaultText(job.getTitle(), ""));
        }
        if (!Boolean.TRUE.equals(job.getPointsCharged())) {
            pointService.deductCustom(job.getUserId(), job.getId(), "COMPUTER_GENERATE", "计算机程序包生成",
                    value(job.getPointsCost()), "生成 " + job.getTitle());
            job.setPointsCharged(true);
        }
        job.setStatus("RUNNING");
        job.setProgress(1);
        job.setCurrentStage("创建生成任务");
        job.setCurrentFile("");
        job.setErrorMessage("");
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateById(job);
        ComputerGenerationConfig snapshot = config;
        taskExecutor.execute(() -> runGenerationPipeline(jobId, snapshot));
        return status(jobId);
    }

    private void runGenerationPipeline(String jobId, ComputerGenerationConfig config) {
        ComputerGenerationJob job = jobMapper.selectById(jobId);
        try {
            ComputerProjectPlan plan = config == null ? analyzeForBackground(job) : planFromConfig(job, config);
            Path jobRoot = ROOT.resolve(job.getUserId().toString()).resolve(job.getId()).normalize();
            assertUnderRoot(jobRoot);
            recreateDirectory(jobRoot);
            fileMapper.delete(new LambdaQueryWrapper<ComputerGeneratedFile>().eq(ComputerGeneratedFile::getJobId, job.getId()));
            previewMapper.delete(new LambdaQueryWrapper<ComputerPreviewInstance>().eq(ComputerPreviewInstance::getJobId, job.getId()));

            TechProfile tech = identifyTechProfile(plan, job.getProjectType(), job.getTechStack(), job.getInputText());
            List<FilePlan> queue = buildFileQueue(plan, tech);
            updateStage(job, "目录生成", 4, "project/");
            int completed = 0;
            List<FileSummary> summaries = new ArrayList<>();
            for (FilePlan file : queue) {
                int progress = 5 + Math.round((completed * 78f) / Math.max(queue.size(), 1));
                updateStage(job, file.stage(), progress, file.path());
                String content = generateValidatedFile(plan, tech, file, summaries);
                Path written = write(jobRoot.resolve(file.path()), content);
                summaries.add(new FileSummary(file.path(), summarize(content)));
                insertFile(job.getId(), jobRoot, written);
                completed++;
            }
            updateStage(job, "预览构建", 86, "preview/index.html");
            ComputerPreviewInstance preview = writePreview(jobRoot, plan, job);
            updateStage(job, "ZIP打包", 94, "毕业设计成果包.zip");
            Path zip = jobRoot.resolve("毕业设计成果包.zip");
            zip(jobRoot, zip);
            fileMapper.delete(new LambdaQueryWrapper<ComputerGeneratedFile>().eq(ComputerGeneratedFile::getJobId, job.getId()));
            insertFile(job.getId(), jobRoot, zip);
            job.setOutputZipPath(zip.toString());
            job.setFrontendPath(jobRoot.resolve("frontend").toString());
            job.setBackendPath(jobRoot.resolve("backend").toString());
            job.setSqlPath(jobRoot.resolve("sql").toString());
            job.setPaperPath(jobRoot.resolve("paper").toString());
            job.setPreviewUrl(preview.getPreviewUrl());
            job.setCurrentFile("");
            updateStage(job, "生成完成", 100, "");
            job.setStatus("SUCCESS");
            job.setUpdatedAt(LocalDateTime.now());
            jobMapper.updateById(job);
        } catch (Exception exception) {
            ComputerGenerationJob failed = jobMapper.selectById(jobId);
            failed.setStatus("FAILED");
            failed.setErrorMessage(stageMessage(failed) + "失败：" + compact(exception.getMessage()));
            failed.setUpdatedAt(LocalDateTime.now());
            jobMapper.updateById(failed);
        }
    }

    public ComputerJobVO status(String jobId) {
        return toVO(requireOwnedJob(jobId), files(jobId), preview(jobId));
    }

    public ComputerJobVO result(String jobId) {
        ComputerGenerationJob job = requireOwnedJob(jobId);
        if (!"SUCCESS".equals(job.getStatus())) return status(jobId);
        return toVO(job, files(jobId), preview(jobId));
    }

    public List<ComputerJobVO> history() {
        Long userId = AuthContext.requireUserId();
        return jobMapper.selectList(new LambdaQueryWrapper<ComputerGenerationJob>()
                        .eq(ComputerGenerationJob::getUserId, userId)
                        .orderByDesc(ComputerGenerationJob::getCreatedAt)
                        .last("LIMIT 30"))
                .stream().map(job -> toVO(job, List.of(), null)).toList();
    }

    @Transactional
    public void delete(String jobId) {
        ComputerGenerationJob job = requireOwnedJob(jobId);
        jobMapper.deleteById(job.getId());
        fileMapper.delete(new LambdaQueryWrapper<ComputerGeneratedFile>().eq(ComputerGeneratedFile::getJobId, job.getId()));
        previewMapper.delete(new LambdaQueryWrapper<ComputerPreviewInstance>().eq(ComputerPreviewInstance::getJobId, job.getId()));
    }

    public Resource download(String jobId) {
        ComputerGenerationJob job = requireOwnedJob(jobId);
        if (!"SUCCESS".equals(job.getStatus()) || job.getOutputZipPath() == null) {
            throw new IllegalStateException("成果包尚未生成完成");
        }
        return safeResource(Paths.get(job.getOutputZipPath()));
    }

    public Resource downloadFile(String jobId, String fileName) {
        requireOwnedJob(jobId);
        ComputerGeneratedFile file = fileMapper.selectOne(new LambdaQueryWrapper<ComputerGeneratedFile>()
                .eq(ComputerGeneratedFile::getJobId, jobId)
                .eq(ComputerGeneratedFile::getFileName, fileName)
                .last("LIMIT 1"));
        if (file == null) throw new IllegalArgumentException("成果文件不存在");
        return safeResource(Paths.get(file.getFilePath()));
    }

    public Resource previewFile(String previewId, String fileName) {
        ComputerPreviewInstance preview = previewMapper.selectOne(new LambdaQueryWrapper<ComputerPreviewInstance>()
                .eq(ComputerPreviewInstance::getPreviewId, previewId));
        if (preview == null) throw new IllegalArgumentException("预览实例不存在");
        Path target = Paths.get(preview.getPreviewPath()).resolve(fileName == null || fileName.isBlank() ? "index.html" : fileName).normalize();
        return safeResource(target);
    }

    public int estimateCost(String projectType, boolean generatePaper, boolean enablePreview) {
        int cost = projectType != null && projectType.contains("混合") ? 120 : 80;
        if (generatePaper) cost += 30;
        if (enablePreview) cost += 20;
        return cost;
    }

    private ComputerProjectPlan analyze(ComputerGenerationJob job) {
        updateStage(job, 0, "RUNNING", "");
        ComputerProjectPlan matrixPlan = generatePlanWithMatrix(job);
        if (matrixPlan != null) {
            matrixPlan = normalizeDomainPlan(job, matrixPlan);
            updateStage(job, 1, "RUNNING", "");
            return matrixPlan;
        }
        return buildPlanFromRequirement(job, null);
    }

    private ComputerProjectPlan normalizeDomainPlan(ComputerGenerationJob job, ComputerProjectPlan plan) {
        if (usesGenericBusinessTables(plan)) return buildPlanFromRequirement(job, plan);
        return plan;
    }

    private static boolean usesGenericBusinessTables(ComputerProjectPlan plan) {
        return plan.tables().stream().anyMatch(table -> table.name().matches("(?i).*(business_record|business_audit|business_notice|record|audit)$"));
    }

    private ComputerProjectPlan buildPlanFromRequirement(ComputerGenerationJob job, ComputerProjectPlan seed) {
        String source = defaultText(job.getTitle(), "") + "\n" + defaultText(job.getInputText(), "") + "\n" +
                (seed == null ? "" : seed.domain() + "\n" + seed.modules() + "\n" + seed.roles());
        String domain = seed == null || seed.domain().isBlank() || "通用业务管理".equals(seed.domain())
                ? extractDomain(source, job.getTitle())
                : seed.domain();
        List<String> roles = mergeDistinct(seed == null ? List.of() : seed.roles(), extractRoles(source));
        List<DomainEntity> entities = extractDomainEntities(source, seed == null ? List.of() : seed.modules());
        List<TablePlan> tables = domainTables(entities, domain);
        List<String> modules = mergeDistinct(seed == null ? List.of() : seed.modules(), entities.stream().map(DomainEntity::module).toList());
        if (!modules.contains("用户认证与权限管理")) modules = prepend(modules, "用户认证与权限管理");
        if (!modules.contains("数据统计")) modules = append(modules, "数据统计");
        List<String> pages = domainPages(entities);
        List<String> apis = domainApis(entities, tables);
        return new ComputerProjectPlan(defaultText(job.getTitle(), seed == null ? "计算机毕业设计管理系统" : seed.title()),
                domain, modules, roles.isEmpty() ? List.of("管理员", "普通用户") : roles,
                tables, pages, apis, seed == null || seed.paperOutline().isEmpty() ? defaultPaperOutline() : seed.paperOutline());
    }

    private static List<TablePlan> genericTables(String base, String domain) {
        String safeBase = "business".equals(base) ? "workflow" : base;
        return List.of(
                new TablePlan("sys_user", "系统用户", List.of("id", "username", "password_hash", "role", "phone", "status", "created_at")),
                new TablePlan(safeBase + "_item", domain + "业务事项", List.of("id", "name", "code", "owner_id", "status", "remark", "created_at")),
                new TablePlan(safeBase + "_process", domain + "流程节点", List.of("id", "item_id", "action", "operator_id", "result", "created_at")),
                new TablePlan("system_notice", domain + "系统公告", List.of("id", "title", "content", "receiver_id", "read_flag", "created_at")),
                new TablePlan("operation_log", "操作日志", List.of("id", "user_id", "operation", "target_type", "target_id", "status", "created_at"))
        );
    }

    private static List<String> pagesFromModules(List<String> modules, List<TablePlan> tables) {
        List<String> pages = new ArrayList<>(List.of("Login", "Dashboard", "UserManage"));
        for (TablePlan table : tables) {
            if (!"sys_user".equals(table.name()) && !"operation_log".equals(table.name())) {
                pages.add(className(table.name()) + "Manage");
            }
        }
        pages.add("Statistics");
        return pages.stream().distinct().toList();
    }

    private static String extractDomain(String source, String title) {
        String cleanTitle = defaultText(title, "").replaceAll("(的设计与实现|设计与实现|系统|平台|项目)$", "").trim();
        if (!cleanTitle.isBlank()) return cleanTitle;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([\u4e00-\u9fa5A-Za-z0-9]{2,30})(系统|平台|项目)").matcher(defaultText(source, ""));
        return matcher.find() ? matcher.group(1) : "任务书驱动软件工程项目";
    }

    private static List<String> extractRoles(String source) {
        List<String> roles = new ArrayList<>();
        for (String role : List.of("管理员", "教师", "学生", "医生", "患者", "读者", "图书管理员", "宿舍管理员", "商家", "会员", "客户", "员工", "财务人员", "审核员")) {
            if (source.contains(role)) roles.add(role);
        }
        if (!roles.contains("管理员")) roles.add(0, "管理员");
        return roles.stream().distinct().toList();
    }

    private static List<DomainEntity> extractDomainEntities(String source, List<String> seedModules) {
        String text = defaultText(source, "") + "\n" + defaultText(String.join("\n", seedModules == null ? List.of() : seedModules), "");
        List<DomainEntity> entities = new ArrayList<>();
        for (DomainEntity candidate : domainVocabulary()) {
            if (containsAny(text, candidate.keywords().toArray(String[]::new))) entities.add(candidate);
        }
        if (entities.stream().noneMatch(entity -> "sys_user".equals(entity.table()))) {
            entities.add(0, new DomainEntity("用户", "用户认证与权限管理", "sys_user", "系统用户",
                    List.of("id", "username", "password_hash", "role", "phone", "status", "created_at"), "UserManage", List.of("用户", "登录", "权限")));
        }
        if (entities.size() < 3) {
            String base = domainSlug(extractDomain(text, ""));
            entities.add(new DomainEntity("事项", "核心事项管理", base + "_item", "核心事项",
                    List.of("id", "name", "code", "owner_id", "status", "remark", "created_at"), className(base + "_item") + "Manage", List.of()));
            entities.add(new DomainEntity("流程", "流程记录管理", base + "_process", "流程记录",
                    List.of("id", "item_id", "action", "operator_id", "result", "status", "created_at"), className(base + "_process") + "Manage", List.of()));
        }
        if (text.contains("公告") || text.contains("通知") || entities.size() < 6) {
            entities.add(new DomainEntity("公告", "系统公告", "system_notice", "系统公告",
                    List.of("id", "title", "content", "publisher_id", "publish_time", "status", "created_at"), "NoticeManage", List.of("公告", "通知")));
        }
        entities.add(new DomainEntity("日志", "操作日志", "operation_log", "操作日志",
                List.of("id", "user_id", "operation", "target_type", "target_id", "ip_address", "status", "created_at"), "OperationLog", List.of("日志", "操作记录")));
        return distinctEntities(entities);
    }

    private static List<DomainEntity> domainVocabulary() {
        return List.of(
                new DomainEntity("用户", "用户认证与权限管理", "sys_user", "系统用户", List.of("id", "username", "password_hash", "role", "phone", "status", "created_at"), "UserManage", List.of("用户", "登录", "权限", "管理员")),
                new DomainEntity("学生", "学生信息管理", "student_profile", "学生信息", List.of("id", "user_id", "student_no", "college", "major", "grade", "status", "created_at"), "StudentManage", List.of("学生")),
                new DomainEntity("教师", "教师信息管理", "teacher_profile", "教师信息", List.of("id", "user_id", "teacher_no", "college", "title", "status", "created_at"), "TeacherManage", List.of("教师", "老师")),
                new DomainEntity("习题", "习题管理", "exercise", "习题", List.of("id", "title", "content", "answer", "difficulty", "creator_id", "status", "created_at"), "ExerciseManage", List.of("习题", "题库", "试题")),
                new DomainEntity("习题发布", "习题发布", "exercise_publish", "习题发布", List.of("id", "exercise_id", "teacher_id", "target_class", "start_time", "end_time", "status", "created_at"), "ExercisePublish", List.of("习题发布", "作业发布", "发布习题")),
                new DomainEntity("答题记录", "答题记录", "answer_record", "答题记录", List.of("id", "exercise_id", "student_id", "answer_content", "score", "is_correct", "status", "created_at"), "AnswerRecord", List.of("答题", "答题记录", "学生答题")),
                new DomainEntity("错题分析", "AI错题分析", "wrong_question_analysis", "AI错题分析", List.of("id", "student_id", "answer_record_id", "analysis_result", "suggestion", "status", "created_at"), "WrongQuestionAnalysis", List.of("错题", "错题分析")),
                new DomainEntity("AI聊天会话", "AI聊天", "ai_chat_session", "AI聊天会话", List.of("id", "user_id", "session_title", "scene_type", "safety_notice", "status", "created_at"), "AiChat", List.of("AI聊天", "大模型", "智能问答", "聊天")),
                new DomainEntity("AI聊天消息", "聊天记录", "ai_chat_message", "AI聊天消息", List.of("id", "session_id", "sender_type", "message_content", "safety_level", "status", "created_at"), "AiChat", List.of("聊天记录", "AI聊天", "聊天")),
                new DomainEntity("心理分析", "心理分析", "psychological_analysis", "心理辅助分析", List.of("id", "student_id", "source_text", "analysis_result", "risk_level", "suggestion", "status", "created_at"), "PsychologicalAnalysis", List.of("心理", "心理分析")),
                new DomainEntity("图书", "图书档案", "book", "图书", List.of("id", "isbn", "title", "author", "category", "stock", "status", "created_at"), "BookManage", List.of("图书", "书籍")),
                new DomainEntity("借阅", "借阅归还", "borrow_record", "借阅记录", List.of("id", "book_id", "reader_id", "borrow_time", "return_time", "status", "created_at"), "BorrowRecord", List.of("借阅", "归还")),
                new DomainEntity("医生", "医生管理", "doctor", "医生", List.of("id", "name", "department", "title", "phone", "status", "created_at"), "DoctorManage", List.of("医生")),
                new DomainEntity("患者", "患者管理", "patient", "患者", List.of("id", "name", "phone", "gender", "id_card", "status", "created_at"), "PatientManage", List.of("患者", "病人")),
                new DomainEntity("预约", "预约管理", "appointment", "预约", List.of("id", "user_id", "resource_id", "appointment_time", "audit_status", "status", "created_at"), "AppointmentManage", List.of("预约", "挂号", "预订")),
                new DomainEntity("商品", "商品管理", "product", "商品", List.of("id", "name", "category", "price", "stock", "status", "created_at"), "ProductManage", List.of("商品")),
                new DomainEntity("订单", "订单管理", "order_info", "订单", List.of("id", "order_no", "user_id", "total_amount", "pay_status", "status", "created_at"), "OrderManage", List.of("订单")),
                new DomainEntity("宿舍", "宿舍管理", "dormitory", "宿舍", List.of("id", "building", "room_no", "capacity", "used_count", "status", "created_at"), "DormitoryManage", List.of("宿舍", "寝室", "公寓")),
                new DomainEntity("报修", "报修管理", "repair_order", "报修工单", List.of("id", "user_id", "target_location", "description", "handler_id", "status", "created_at"), "RepairManage", List.of("报修", "维修")),
                new DomainEntity("公告", "系统公告", "system_notice", "系统公告", List.of("id", "title", "content", "publisher_id", "publish_time", "status", "created_at"), "NoticeManage", List.of("公告", "通知"))
        );
    }

    private static List<DomainEntity> distinctEntities(List<DomainEntity> entities) {
        List<DomainEntity> result = new ArrayList<>();
        for (DomainEntity entity : entities) {
            if (result.stream().noneMatch(item -> item.table().equals(entity.table()))) result.add(entity);
        }
        return result;
    }

    private static List<TablePlan> domainTables(List<DomainEntity> entities, String domain) {
        return entities.stream().map(entity -> new TablePlan(entity.table(), entity.comment(), entity.fields())).toList();
    }

    private static List<String> domainPages(List<DomainEntity> entities) {
        List<String> pages = new ArrayList<>(List.of("Login", "Dashboard"));
        pages.addAll(entities.stream()
                .map(DomainEntity::page)
                .filter(page -> !page.equals("OperationLog"))
                .toList());
        pages.add("Statistics");
        return pages.stream().distinct().toList();
    }

    private static List<String> domainApis(List<DomainEntity> entities, List<TablePlan> tables) {
        List<String> apis = new ArrayList<>();
        apis.add("/api/auth - 登录、退出、权限校验接口");
        for (DomainEntity entity : entities) {
            if (!"operation_log".equals(entity.table())) {
                apis.add("/api/" + entity.table().replace("_", "-") + " - " + entity.comment() + "接口");
            }
        }
        apis.add("/api/statistics - 数据统计接口");
        return apis.stream().distinct().toList();
    }

    private static List<String> mergeDistinct(List<String> first, List<String> second) {
        List<String> values = new ArrayList<>();
        if (first != null) values.addAll(first);
        if (second != null) values.addAll(second);
        return values.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
    }

    private static List<String> prepend(List<String> values, String value) {
        List<String> result = new ArrayList<>();
        result.add(value);
        result.addAll(values);
        return result.stream().distinct().toList();
    }

    private static List<String> append(List<String> values, String value) {
        List<String> result = new ArrayList<>(values);
        result.add(value);
        return result.stream().distinct().toList();
    }

    private ComputerProjectPlan generatePlanWithMatrix(ComputerGenerationJob job) {
        if (!matrixDesignService.apiKeyConfigured()) {
            return null;
        }
        String instructions = """
                你是一名资深软件架构师、毕业设计指导教师和全栈开发专家。
                你的任务是根据用户上传的毕业设计任务书、开题报告，自动生成完整的软件工程项目方案。

                项目定位：
                本系统仅用于毕业设计辅助、课程设计辅助、教学演示项目、企业管理系统原型、数据分析与信息管理系统。
                生成内容必须符合软件工程规范、数据安全规范、合法合规使用原则。

                允许生成的项目类型：
                管理系统：学生管理系统、图书管理系统、宿舍管理系统、医院预约系统、OA办公系统、CRM系统、ERP系统、电商商城系统。
                数据分析系统：销售数据分析、财务数据分析、教学数据分析、物流数据分析。
                智能应用：微信小程序、Web应用、SpringBoot项目、Vue项目、Python数据分析项目、Flask项目、Django项目。

                禁止生成：漏洞利用工具、爆破工具、扫描工具、网络攻击工具、绕过认证工具、绕过访问控制工具、自动化账号注册工具、验证码绕过工具、代理池系统、未授权数据采集工具、非法监控工具、恶意自动化脚本。
                如果任务书涉及上述内容，只返回 JSON：{"blocked":true,"message":"该需求超出毕业设计与教学项目范围，仅支持合法合规的软件工程项目生成。"}

                数据处理规范：
                允许公开数据分析、用户自行上传的数据处理、教学案例数据处理、合法授权的数据源接入。
                禁止绕过登录的数据获取、绕过验证码的数据获取、未授权接口访问、访问受限制资源、高频自动化抓取。

                安全规范：
                禁止生成默认弱密码，例如 admin/admin123。
                管理员账户设计必须为“首次启动时创建管理员账户”或“系统首次运行时要求用户设置密码”。

                必须主动补全合理需求，不能返回信息不足。不要把所有项目都写成学生管理系统。
                必须先从任务书中提取业务名词，再生成数据库表、后端类名、前端页面名。
                表名和模块名必须体现任务书中的具体业务实体，先抽取领域模型，再规划表、类、页面和接口。
                禁止使用 business_record、business_audit、business_notice、record、audit 这类万能泛化模块替代真实业务模块。
                每个项目生成出来的文件名、表名、页面名必须明显不同。
                只返回 JSON，不要 Markdown，不要解释。
                JSON 结构：
                {
                  "blocked": false,
                  "domain": "业务场景名称",
                  "modules": ["模块1", "模块2"],
                  "roles": ["管理员", "普通用户"],
                  "tables": [{"name":"英文小写表名","comment":"中文表说明","fields":["id","name","status","created_at"]}],
                  "pages": ["登录页", "首页仪表盘"],
                  "apis": ["/api/example - 接口说明"],
                  "paperOutline": ["摘要", "Abstract"],
                  "implementationNotes": ["生成重点"]
                }
                输出目标是完整的软件工程项目设计方案，而不是网络工具、自动化采集工具、安全测试工具或攻击工具。
                表名必须是英文字母、数字、下划线，不要中文；至少 4 张表；模块至少 8 个；接口至少 8 个。
                """;
        String input = """
                项目题目：%s
                项目类型：%s
                技术栈：%s
                用户资料：
                %s
                """.formatted(job.getTitle(), job.getProjectType(), job.getTechStack(), trimForModel(job.getInputText()));
        try {
            String response = matrixDesignService.generate(instructions, input);
            JsonNode root = objectMapper.readTree(stripJson(response));
            if (root.path("blocked").asBoolean(false)) {
                throw new IllegalArgumentException(OUT_OF_SCOPE_MESSAGE);
            }
            String domain = root.path("domain").asText("");
            List<String> modules = readStringList(root.path("modules"));
            List<String> roles = readStringList(root.path("roles"));
            List<TablePlan> tables = new ArrayList<>();
            for (JsonNode table : root.path("tables")) {
                String name = table.path("name").asText("");
                List<String> fields = readStringList(table.path("fields"));
                if (name.matches("[a-zA-Z][a-zA-Z0-9_]{1,60}") && !fields.isEmpty()) {
                    tables.add(new TablePlan(name.toLowerCase(Locale.ROOT), table.path("comment").asText(name), normalizeFields(fields)));
                }
            }
            List<String> pages = readStringList(root.path("pages"));
            List<String> apis = readStringList(root.path("apis"));
            List<String> outline = readStringList(root.path("paperOutline"));
            if (domain.isBlank() || modules.size() < 5 || tables.size() < 3 || apis.size() < 3) {
                return null;
            }
            return new ComputerProjectPlan(job.getTitle(), domain, modules, roles.isEmpty() ? List.of("管理员", "普通用户") : roles,
                    tables, pages.isEmpty() ? List.of("登录页", "首页仪表盘", "核心业务页", "数据统计页", "用户管理页") : pages,
                    apis, outline.isEmpty() ? defaultPaperOutline() : outline);
        } catch (Exception exception) {
            job.setErrorMessage("豆包规划失败，已回退规则生成：" + compact(exception.getMessage()));
            jobMapper.updateById(job);
            return null;
        }
    }

    private Path writeSql(Path root, ComputerProjectPlan plan) throws IOException {
        Path dir = root.resolve("sql");
        Files.createDirectories(dir);
        List<TablePlan> tables = ensureSystemSetupTable(plan.tables());
        StringBuilder sql = new StringBuilder("CREATE DATABASE IF NOT EXISTS dropai_generated DEFAULT CHARSET utf8mb4;\nUSE dropai_generated;\n\n");
        for (TablePlan table : tables) {
            sql.append("CREATE TABLE IF NOT EXISTS ").append(table.name()).append(" (\n");
            for (String field : table.fields()) {
                if ("id".equals(field)) sql.append("  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',\n");
                else if (field.endsWith("_id")) sql.append("  ").append(field).append(" BIGINT COMMENT '关联ID',\n");
                else if (field.endsWith("_at")) sql.append("  ").append(field).append(" DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '时间字段',\n");
                else sql.append("  ").append(field).append(" VARCHAR(255) COMMENT '").append(field).append("',\n");
            }
            sql.append("  INDEX idx_").append(table.name()).append("_status (status)\n) COMMENT='").append(table.comment()).append("';\n\n");
        }
        sql.append("INSERT INTO system_setup(setup_key,setup_value,status) VALUES ('admin_password_policy','FIRST_RUN_SET_PASSWORD','PENDING');\n");
        sql.append("INSERT INTO ").append(tables.get(1).name()).append("(name,code,status,remark) VALUES ('示例").append(plan.domain()).append("记录','DEMO001','ACTIVE','系统生成示例数据');\n");
        return write(dir.resolve("schema.sql"), sql.toString());
    }

    private Path writeBackend(Path root, ComputerProjectPlan plan, String techStack) throws IOException {
        Path backend = root.resolve(techStack != null && techStack.toLowerCase(Locale.ROOT).contains("python") ? "backend-python" : "backend-java");
        Files.createDirectories(backend);
        if (backend.getFileName().toString().contains("python")) {
            write(backend.resolve("app.py"), "from fastapi import FastAPI\n\napp = FastAPI(title='" + plan.title() + "')\n\n@app.get('/api/health')\ndef health():\n    return {'status':'ok','project':'" + plan.title() + "'}\n");
            write(backend.resolve("requirements.txt"), "fastapi\nuvicorn\nsqlalchemy\npymysql\n");
        } else {
            Path src = backend.resolve("src/main/java/com/dropai/generated");
            Files.createDirectories(src.resolve("controller"));
            Files.createDirectories(src.resolve("service"));
            Files.createDirectories(src.resolve("entity"));
            write(backend.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion><groupId>com.dropai</groupId><artifactId>generated</artifactId><version>1.0.0</version></project>\n");
            write(src.resolve("common/Result.java"), "package com.dropai.generated.common;\npublic record Result<T>(int code,String message,T data){public static <T> Result<T> ok(T data){return new Result<>(200,\"success\",data);}}\n");
            write(src.resolve("controller/" + safeClass(plan.domain()) + "Controller.java"), "package com.dropai.generated.controller;\nimport com.dropai.generated.common.Result;\nimport org.springframework.web.bind.annotation.*;\n@RestController\n@RequestMapping(\"/api/" + safeSlug(plan.domain()) + "\")\npublic class " + safeClass(plan.domain()) + "Controller {\n @GetMapping(\"/page\") public Result<String> page(){return Result.ok(\"" + plan.domain() + "分页查询\");}\n @PostMapping public Result<String> save(){return Result.ok(\"保存成功\");}\n}\n");
        }
        write(backend.resolve("README.md"), "# 后端说明\n\n项目：" + plan.title() + "\n\n核心接口：\n" + String.join("\n", plan.apis()));
        return backend;
    }

    private Path writeFrontend(Path root, ComputerProjectPlan plan) throws IOException {
        Path frontend = root.resolve("frontend");
        Files.createDirectories(frontend.resolve("src/views"));
        write(frontend.resolve("package.json"), "{\"scripts\":{\"dev\":\"vite --host 0.0.0.0\"},\"dependencies\":{\"@vitejs/plugin-vue\":\"latest\",\"vite\":\"latest\",\"vue\":\"latest\",\"element-plus\":\"latest\"}}\n");
        write(frontend.resolve("src/main.js"), "import { createApp } from 'vue'\nimport App from './views/App.vue'\ncreateApp(App).mount('#app')\n");
        write(frontend.resolve("src/views/App.vue"), "<template><main><h1>" + plan.title() + "</h1><p>" + plan.domain() + "</p></main></template>\n");
        return frontend;
    }

    private Path writePaper(Path root, ComputerProjectPlan plan, String techStack) throws IOException {
        Path dir = root.resolve("paper");
        Files.createDirectories(dir);
        String md = generatePaperWithMatrix(plan, techStack);
        if (md.isBlank()) {
            md = "# " + plan.title() + "毕业论文\n\n## 摘要\n本文围绕" + plan.domain() + "场景，设计并实现一套包含前端、后端和数据库的毕业设计系统。\n\n" +
                "## Abstract\nThis paper designs and implements a " + plan.domain() + " system with frontend, backend and database modules.\n\n" +
                "## 绪论\n项目针对" + plan.domain() + "中的信息录入、流程处理、统计分析和权限控制问题展开。\n\n" +
                "## 相关技术介绍\n技术栈：" + techStack + "。\n\n## 系统需求分析\n功能模块：" + String.join("、", plan.modules()) + "。\n\n" +
                "## 系统总体设计\n系统采用前后端分离结构，后端提供统一 REST 接口，前端提供管理端和普通用户端。\n\n" +
                "## 数据库设计\n主要数据表：" + plan.tables().stream().map(TablePlan::name).reduce((a, b) -> a + "、" + b).orElse("") + "。\n\n" +
                "## 系统详细设计\n接口包括：" + String.join("；", plan.apis()) + "。\n\n## 系统实现\n完成" + String.join("、", plan.pages()) + "等页面。\n\n" +
                "## 系统测试\n测试用例覆盖登录、数据新增、分页查询、异常输入和权限访问。\n\n## 结论\n系统满足" + plan.domain() + "毕业设计的完整交付要求。\n\n## 参考文献\n[1] Spring Boot 官方文档。\n[2] Vue 官方文档。\n\n## 致谢\n感谢指导教师在需求分析和系统设计阶段给予的帮助。\n";
        }
        return write(dir.resolve("thesis.md"), md);
    }

    private String generatePaperWithMatrix(ComputerProjectPlan plan, String techStack) {
        if (!matrixDesignService.apiKeyConfigured()) {
            return "";
        }
        String instructions = """
                你是计算机专业本科毕业论文写作助手。根据已规划的真实系统内容写论文 Markdown。
                必须包含：摘要、Abstract、绪论、相关技术介绍、系统需求分析、系统总体设计、数据库设计、系统详细设计、系统实现、系统测试、结论、参考文献、致谢。
                内容必须贴合项目题目、模块、表和接口，不要空泛描述。直接返回 Markdown。
                论文和方案仅面向毕业设计、课程设计、教学演示、企业管理系统原型、数据分析与信息管理系统。
                不得生成网络工具、自动化采集工具、安全测试工具、攻击工具、漏洞利用、爆破、扫描、绕过认证、绕过验证码、代理池或未授权数据采集相关内容。
                账号安全设计必须说明“首次启动时创建管理员账户”或“系统首次运行时要求用户设置密码”，不得出现默认弱密码。
                """;
        String input = """
                题目：%s
                业务场景：%s
                技术栈：%s
                用户角色：%s
                模块：%s
                前端页面：%s
                数据表：%s
                接口：%s
                """.formatted(plan.title(), plan.domain(), techStack, plan.roles(), plan.modules(), plan.pages(), plan.tables(), plan.apis());
        try {
            String text = matrixDesignService.generate(instructions, input).trim();
            return text.startsWith("#") ? text : "# " + plan.title() + "毕业论文\n\n" + text;
        } catch (Exception exception) {
            return "";
        }
    }

    private ComputerPreviewInstance writePreview(Path root, ComputerProjectPlan plan, ComputerGenerationJob job) throws IOException {
        String previewId = "pv_" + UUID.randomUUID().toString().replace("-", "");
        Path dir = root.resolve("preview").resolve(previewId);
        Files.createDirectories(dir);
        String nav = "<nav><a href='index.html'>登录</a><a href='dashboard.html'>仪表盘</a><a href='business.html'>业务</a><a href='statistics.html'>统计</a><a href='user.html'>用户</a></nav>";
        String css = "<style>body{margin:0;font-family:Arial,'Microsoft YaHei',sans-serif;background:#f4f7fb;color:#172033}nav{display:flex;gap:10px;padding:14px 22px;background:#0f172a}nav a{color:white;text-decoration:none}.wrap{padding:28px}.cards{display:grid;grid-template-columns:repeat(3,1fr);gap:14px}.card{background:white;border:1px solid #dbe5f4;border-radius:8px;padding:18px}button{background:#2563eb;color:white;border:0;border-radius:6px;padding:10px 18px}</style>";
        write(dir.resolve("index.html"), css + nav + "<div class='wrap'><h1>" + plan.title() + "</h1><p>" + plan.domain() + "登录页</p><button>登录系统</button></div>");
        write(dir.resolve("dashboard.html"), css + nav + "<div class='wrap'><h1>首页仪表盘</h1><div class='cards'><div class='card'>待办 18</div><div class='card'>本月新增 42</div><div class='card'>异常提醒 3</div></div></div>");
        write(dir.resolve("business.html"), css + nav + "<div class='wrap'><h1>核心业务</h1><div class='cards'>" + plan.modules().stream().map(m -> "<div class='card'><b>" + m + "</b><p>支持新增、编辑、审核和查询。</p></div>").reduce("", String::concat) + "</div></div>");
        write(dir.resolve("statistics.html"), css + nav + "<div class='wrap'><h1>数据统计</h1><div class='card'>按状态、月份和业务类型生成统计报表。</div></div>");
        write(dir.resolve("user.html"), css + nav + "<div class='wrap'><h1>用户管理</h1><div class='card'>管理员、普通用户、权限角色和登录日志。</div></div>");
        String url = "/api/computer-generator/preview-content/" + previewId + "/index.html";
        ComputerPreviewInstance preview = new ComputerPreviewInstance();
        preview.setId("cpi_" + UUID.randomUUID().toString().replace("-", ""));
        preview.setJobId(job.getId());
        preview.setPreviewId(previewId);
        preview.setPreviewPath(dir.toString());
        preview.setPreviewUrl(url);
        preview.setStatus("RUNNING");
        preview.setCreatedAt(LocalDateTime.now());
        preview.setExpiredAt(LocalDateTime.now().plusDays(3));
        previewMapper.insert(preview);
        return preview;
    }

    private void writeReadme(Path root, ComputerProjectPlan plan, ComputerGenerationJob job) throws IOException {
        write(root.resolve("README.md"), "# " + plan.title() + "\n\n业务场景：" + plan.domain() + "\n\n目录包含 frontend、backend、sql、paper、preview。\n\n运行说明：导入 sql/schema.sql，启动后端，再运行前端 Vite 项目。\n\n安全说明：系统首次运行时要求用户创建管理员账户并设置强密码，不提供默认管理员弱密码。\n");
    }

    private void registerFiles(String jobId, Path root, Path zip) throws IOException {
        fileMapper.delete(new LambdaQueryWrapper<ComputerGeneratedFile>().eq(ComputerGeneratedFile::getJobId, jobId));
        List<Path> paths;
        try (var stream = Files.walk(root)) {
            paths = stream.filter(Files::isRegularFile).filter(path -> !path.equals(zip)).toList();
        }
        for (Path path : paths) insertFile(jobId, root, path);
        insertFile(jobId, root, zip);
    }

    private void insertFile(String jobId, Path root, Path path) throws IOException {
        ComputerGeneratedFile file = new ComputerGeneratedFile();
        file.setId("cgf_" + UUID.randomUUID().toString().replace("-", ""));
        file.setJobId(jobId);
        file.setFileName(root.relativize(path).toString().replace("\\", "/"));
        file.setFilePath(path.toString());
        file.setFileSize(Files.size(path));
        file.setFileType(file.getFileName().contains("/") ? file.getFileName().substring(0, file.getFileName().indexOf('/')) : "package");
        file.setCreatedAt(LocalDateTime.now());
        fileMapper.insert(file);
    }

    private List<ComputerGeneratedFile> files(String jobId) {
        return fileMapper.selectList(new LambdaQueryWrapper<ComputerGeneratedFile>()
                .eq(ComputerGeneratedFile::getJobId, jobId)
                .orderByAsc(ComputerGeneratedFile::getFileName));
    }

    private ComputerPreviewInstance preview(String jobId) {
        return previewMapper.selectOne(new LambdaQueryWrapper<ComputerPreviewInstance>()
                .eq(ComputerPreviewInstance::getJobId, jobId)
                .orderByDesc(ComputerPreviewInstance::getCreatedAt)
                .last("LIMIT 1"));
    }

    private ComputerJobVO toVO(ComputerGenerationJob job, List<ComputerGeneratedFile> files, ComputerPreviewInstance preview) {
        return new ComputerJobVO(job.getId(), job.getTitle(), job.getProjectType(), job.getTechStack(), job.getStatus(),
                value(job.getProgress()), stageMessage(job), defaultText(job.getCurrentFile(), ""),
                job.getErrorMessage(), value(job.getPointsCost()),
                job.getPreviewUrl(), "/api/computer-generator/download/" + job.getId(),
                files.stream().filter(file -> file.getFileName() != null && file.getFileName().endsWith(".zip"))
                        .map(file -> new GeneratedFileVO(file.getFileType(), "毕业设计成果包.zip", value(file.getFileSize()),
                        "/api/computer-generator/download-file/" + job.getId() + "?fileName=" + urlEncode(file.getFileName()))).toList(),
                preview == null ? null : preview.getPreviewUrl(), STAGES);
    }

    private ComputerPlanVO toPlanVO(ComputerProjectPlan plan, String projectType, String techStack, int cost) {
        TechProfile tech = identifyTechProfile(plan, projectType, techStack, "");
        List<FilePlan> queue = buildFileQueue(plan, tech);
        return new ComputerPlanVO(plan.title(), tech.projectType(), tech.displayStack(), plan.roles(), plan.modules(),
                plan.tables().stream().map(table -> new TablePlanVO(table.name(), table.comment(), table.fields())).toList(),
                plan.pages(), plan.apis(), plan.paperOutline(), tech.language(), tech.frontendStack(), tech.backendStack(),
                tech.databaseType(), tech.needMiniprogram(), tech.needDesktop(), tech.needDataAnalysis(),
                projectDirectoryTree(queue), queue.stream().map(FilePlanVO::from).toList(),
                cost, true, true, true);
    }

    private ComputerProjectPlan planFromConfig(ComputerGenerationJob job, ComputerGenerationConfig config) {
        List<TablePlan> tables = config.tables() == null ? List.of() : config.tables().stream()
                .filter(table -> table.name() != null && table.name().matches("[a-zA-Z][a-zA-Z0-9_]{1,60}"))
                .map(table -> new TablePlan(table.name().toLowerCase(Locale.ROOT), defaultText(table.comment(), table.name()), normalizeFields(table.fields() == null ? List.of() : table.fields())))
                .toList();
        if (tables.isEmpty()) tables = analyze(job).tables();
        return new ComputerProjectPlan(
                defaultText(config.title(), job.getTitle()),
                defaultText(config.projectType(), job.getProjectType()),
                nonEmpty(config.modules(), List.of("用户认证", "首页仪表盘", "核心业务管理", "数据统计")),
                nonEmpty(config.roles(), List.of("管理员", "普通用户")),
                tables,
                nonEmpty(config.pages(), List.of("登录页", "首页仪表盘", "核心业务页", "数据统计页", "用户管理页")),
                nonEmpty(config.apis(), tables.stream().map(table -> "/api/" + table.name() + " - CRUD、分页查询、统计接口").toList()),
                nonEmpty(config.paperOutline(), defaultPaperOutline())
        );
    }

    private void applyConfig(ComputerGenerationJob job, ComputerGenerationConfig config) {
        job.setTitle(defaultText(config.title(), job.getTitle()));
        job.setProjectType(defaultText(config.projectType(), job.getProjectType()));
        String configuredStack = defaultText(config.techStack(), "");
        if (configuredStack.isBlank()) {
            List<String> stackParts = List.of(defaultText(config.programmingLanguage(), ""), defaultText(config.backendStack(), ""), defaultText(config.frontendStack(), ""), defaultText(config.databaseType(), ""))
                    .stream().filter(part -> !part.isBlank()).toList();
            configuredStack = String.join(" | ", stackParts);
        }
        job.setTechStack(defaultText(configuredStack, job.getTechStack()));
        job.setPointsCost(estimateCost(job.getProjectType(), config.generatePaper(), config.enablePreview()));
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateById(job);
    }

    private ComputerProjectPlan analyzeForBackground(ComputerGenerationJob job) {
        ComputerProjectPlan matrixPlan = generatePlanWithMatrix(job);
        if (matrixPlan != null) return normalizeDomainPlan(job, matrixPlan);
        String type = recommendProjectType(new ComputerProjectPlan(job.getTitle(), defaultText(job.getProjectType(), "通用业务管理"), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()), job.getInputText());
        return planFromConfig(job, new ComputerGenerationConfig(job.getTitle(), type, job.getTechStack(),
                List.of("管理员", "普通用户"), null, null, null, null, false, false, false,
                List.of("用户认证", "首页仪表盘", "核心业务管理", "数据统计"),
                List.of(new TablePlanVO("sys_user", "系统用户", List.of("id", "username", "password_hash", "role", "status", "created_at")),
                        new TablePlanVO("workflow_item", "流程事项", List.of("id", "name", "code", "status", "remark", "created_at"))),
                List.of("登录页", "首页仪表盘", "核心业务页", "数据统计页", "用户管理页"),
                List.of("/api/users - 用户管理接口", "/api/workflow-items - 流程事项接口"),
                defaultPaperOutline(), true, true, true));
    }

    private TechProfile identifyTechProfile(ComputerProjectPlan plan, String projectType, String techStack, String inputText) {
        String text = (defaultText(projectType, "") + " " + defaultText(techStack, "") + " " + defaultText(inputText, "") + " " + plan.domain()).toLowerCase(Locale.ROOT);
        boolean miniprogram = containsAny(text, "微信", "小程序", "miniprogram", "wxapp");
        boolean analysis = containsAny(text, "pandas", "streamlit", "dash", "matplotlib", "数据分析", "可视化大屏", "财务分析", "销售分析", "教学数据", "物流数据", "公开数据");
        boolean desktop = containsAny(text, "桌面端", "桌面应用", "electron", "javafx", "winform");
        String normalizedType = defaultText(projectType, recommendProjectType(plan, inputText));
        String language = "Java";
        String backend = "SpringBoot 3.x + MyBatis-Plus";
        String frontend = "Vue3 + Element Plus + Vite";
        String database = text.contains("sqlite") ? "SQLite" : "MySQL 8";
        if (analysis) {
            normalizedType = "Python数据分析项目";
            language = "Python";
            backend = "Flask dashboard";
            frontend = "Jinja2 + ECharts";
            database = text.contains("mysql") ? "MySQL" : "SQLite";
        } else if (miniprogram) {
            normalizedType = "微信小程序项目";
            language = "JavaScript";
            backend = text.contains("node") || text.contains("express") ? "Node.js + Express" : "SpringBoot 3.x";
            frontend = "微信原生小程序";
            database = "MySQL";
        } else if (containsAny(text, "django")) {
            normalizedType = "Django项目";
            language = "Python";
            backend = "Django + Django ORM";
            frontend = "Vue3 + Element Plus";
        } else if (containsAny(text, "flask")) {
            normalizedType = "Flask项目";
            language = "Python";
            backend = "Flask + SQLAlchemy";
            frontend = text.contains("jinja") ? "Jinja2 templates" : "Vue3 + Element Plus";
        } else if (containsAny(text, "node", "express")) {
            normalizedType = "Node.js Express项目";
            language = "JavaScript";
            backend = "Node.js + Express + Sequelize";
            frontend = text.contains("react") ? "React" : "Vue3 + Element Plus";
            database = text.contains("mysql") ? "MySQL" : "SQLite";
        }
        String stack = language + " | backend: " + backend + " | frontend: " + frontend + " | database: " + database;
        return new TechProfile(normalizedType, language, backend, frontend, database, miniprogram, desktop, analysis, stack);
    }

    private List<FilePlan> buildFileQueue(ComputerProjectPlan plan, TechProfile tech) {
        List<FilePlan> queue = new ArrayList<>();
        int priority = 1;
        priority = addFile(queue, "sql/schema.sql", "SQL生成", "数据库建表脚本", List.of(), priority);
        priority = addFile(queue, "sql/data.sql", "SQL生成", "初始化演示数据", List.of("sql/schema.sql"), priority);
        priority = addFile(queue, "sql/init.sql", "SQL生成", "数据库初始化入口脚本", List.of("sql/schema.sql", "sql/data.sql"), priority);
        if (tech.needDataAnalysis()) {
            priority = buildAnalysisQueue(queue, plan, priority);
        } else if (tech.needMiniprogram()) {
            priority = buildMiniprogramQueue(queue, plan, tech, priority);
        } else if (tech.backendStack().contains("Django")) {
            priority = buildDjangoQueue(queue, plan, priority);
        } else if (tech.backendStack().contains("Flask")) {
            priority = buildFlaskQueue(queue, plan, priority);
        } else if (tech.backendStack().contains("Express")) {
            priority = buildNodeQueue(queue, plan, priority);
        } else {
            priority = buildSpringVueQueue(queue, plan, priority);
        }
        priority = addFile(queue, "README.md", "论文生成", "项目运行说明", List.of(), priority);
        priority = addFile(queue, "paper/论文大纲.md", "论文生成", "论文大纲", List.of("README.md"), priority);
        addFile(queue, "paper/毕业论文.docx", "论文生成", "可打开的论文文档内容", List.of("paper/论文大纲.md"), priority);
        return queue;
    }

    private int buildSpringVueQueue(List<FilePlan> queue, ComputerProjectPlan plan, int priority) {
        priority = addFile(queue, "backend/pom.xml", "后端生成", "Maven工程配置", List.of(), priority);
        priority = addFile(queue, "backend/src/main/resources/application.yml", "后端生成", "SpringBoot运行配置", List.of("backend/pom.xml"), priority);
        priority = addFile(queue, "backend/src/main/java/com/dropai/generated/GeneratedApplication.java", "后端生成", "SpringBoot启动类", List.of("backend/pom.xml"), priority);
        priority = addFile(queue, "backend/src/main/java/com/dropai/generated/common/Result.java", "后端生成", "统一响应对象", List.of(), priority);
        priority = addFile(queue, "backend/src/main/java/com/dropai/generated/common/PageResult.java", "后端生成", "分页响应对象", List.of(), priority);
        priority = addFile(queue, "backend/src/main/java/com/dropai/generated/common/BusinessException.java", "后端生成", "业务异常对象", List.of(), priority);
        priority = addFile(queue, "backend/src/main/java/com/dropai/generated/dto/LoginRequest.java", "后端生成", "登录请求DTO", List.of(), priority);
        priority = addFile(queue, "backend/src/main/java/com/dropai/generated/dto/PageQuery.java", "后端生成", "分页查询DTO", List.of(), priority);
        priority = addFile(queue, "backend/src/main/java/com/dropai/generated/config/WebConfig.java", "后端生成", "Web与跨域配置", List.of(), priority);
        priority = addFile(queue, "backend/src/main/java/com/dropai/generated/config/CorsConfig.java", "后端生成", "跨域配置", List.of(), priority);
        priority = addFile(queue, "backend/src/main/java/com/dropai/generated/config/MyBatisPlusConfig.java", "后端生成", "MyBatis-Plus配置", List.of(), priority);
        if (hasAiModules(plan)) {
            priority = addFile(queue, "backend/src/main/java/com/dropai/generated/config/AiModelConfig.java", "后端生成", "大模型配置，API Key从环境变量读取", List.of("backend/src/main/resources/application.yml"), priority);
            priority = addFile(queue, "backend/src/main/java/com/dropai/generated/service/AiAnalysisService.java", "后端生成", "AI分析服务接口", List.of("backend/src/main/java/com/dropai/generated/config/AiModelConfig.java"), priority);
            priority = addFile(queue, "backend/src/main/java/com/dropai/generated/service/impl/AiAnalysisServiceImpl.java", "后端生成", "AI错题与心理辅助分析实现", List.of("backend/src/main/java/com/dropai/generated/service/AiAnalysisService.java"), priority);
            priority = addFile(queue, "backend/src/main/java/com/dropai/generated/controller/AiChatController.java", "后端生成", "AI聊天接口与内容安全提示", List.of("backend/src/main/java/com/dropai/generated/service/AiAnalysisService.java"), priority);
        }
        for (TablePlan table : ensureSystemSetupTable(plan.tables())) {
            String name = className(table.name());
            priority = addFile(queue, "backend/src/main/java/com/dropai/generated/entity/" + name + ".java", "后端生成", table.comment() + "实体类", List.of("sql/schema.sql"), priority);
            priority = addFile(queue, "backend/src/main/java/com/dropai/generated/mapper/" + name + "Mapper.java", "后端生成", table.comment() + "Mapper接口", List.of("backend/src/main/java/com/dropai/generated/entity/" + name + ".java"), priority);
            priority = addFile(queue, "backend/src/main/resources/mapper/" + name + "Mapper.xml", "后端生成", table.comment() + "Mapper XML", List.of("backend/src/main/java/com/dropai/generated/mapper/" + name + "Mapper.java"), priority);
            priority = addFile(queue, "backend/src/main/java/com/dropai/generated/service/" + name + "Service.java", "后端生成", table.comment() + "业务接口", List.of("backend/src/main/java/com/dropai/generated/entity/" + name + ".java"), priority);
            priority = addFile(queue, "backend/src/main/java/com/dropai/generated/service/impl/" + name + "ServiceImpl.java", "后端生成", table.comment() + "业务实现", List.of("backend/src/main/java/com/dropai/generated/service/" + name + "Service.java"), priority);
            priority = addFile(queue, "backend/src/main/java/com/dropai/generated/controller/" + name + "Controller.java", "后端生成", table.comment() + "REST接口", List.of("backend/src/main/java/com/dropai/generated/service/" + name + "Service.java"), priority);
        }
        return buildVueQueue(queue, plan, priority);
    }

    private int buildVueQueue(List<FilePlan> queue, ComputerProjectPlan plan, int priority) {
        priority = addFile(queue, "frontend/package.json", "前端生成", "前端依赖配置", List.of(), priority);
        priority = addFile(queue, "frontend/vite.config.js", "前端生成", "Vite配置", List.of("frontend/package.json"), priority);
        priority = addFile(queue, "frontend/index.html", "前端生成", "前端入口HTML", List.of("frontend/package.json"), priority);
        priority = addFile(queue, "frontend/src/api/request.js", "前端生成", "Axios请求封装", List.of(), priority);
        for (TablePlan table : ensureSystemSetupTable(plan.tables())) {
            priority = addFile(queue, "frontend/src/api/" + table.name() + ".js", "前端生成", table.comment() + "接口封装", List.of("frontend/src/api/request.js"), priority);
        }
        priority = addFile(queue, "frontend/src/router/index.js", "前端生成", "Vue Router路由", List.of(), priority);
        priority = addFile(queue, "frontend/src/components/DataCard.vue", "前端生成", "通用数据卡片组件", List.of(), priority);
        priority = addFile(queue, "frontend/src/views/Login.vue", "前端生成", "登录页", List.of("frontend/src/api/request.js"), priority);
        priority = addFile(queue, "frontend/src/views/Dashboard.vue", "前端生成", "首页仪表盘", List.of("frontend/src/components/DataCard.vue"), priority);
        priority = addFile(queue, "frontend/src/views/Business.vue", "前端生成", "核心业务管理页", List.of("frontend/src/api/request.js"), priority);
        priority = addFile(queue, "frontend/src/views/Statistics.vue", "前端生成", "数据统计页", List.of("frontend/src/components/DataCard.vue"), priority);
        priority = addFile(queue, "frontend/src/views/UserManage.vue", "前端生成", "用户管理页", List.of("frontend/src/api/request.js"), priority);
        for (String page : plan.pages()) {
            String component = pageComponentName(page);
            if (!List.of("Login", "Dashboard", "Statistics", "UserManage").contains(component)) {
                priority = addFile(queue, "frontend/src/views/" + component + ".vue", "前端生成", page + "页面", List.of("frontend/src/api/request.js"), priority);
            }
        }
        for (TablePlan table : plan.tables()) {
            if (hasPageForTable(plan.pages(), table.name())) continue;
            priority = addFile(queue, "frontend/src/views/" + className(table.name()) + "Manage.vue", "前端生成", table.comment() + "独立管理页", List.of("frontend/src/api/" + table.name() + ".js"), priority);
        }
        priority = addFile(queue, "frontend/src/App.vue", "前端生成", "根组件", List.of("frontend/src/router/index.js"), priority);
        return addFile(queue, "frontend/src/main.js", "前端生成", "前端启动入口", List.of("frontend/src/App.vue"), priority);
    }

    private int buildSpringBackendQueue(List<FilePlan> queue, ComputerProjectPlan plan, int priority) {
        priority = addFile(queue, "backend/pom.xml", "后端生成", "Maven工程配置", List.of(), priority);
        priority = addFile(queue, "backend/src/main/resources/application.yml", "后端生成", "SpringBoot运行配置", List.of("backend/pom.xml"), priority);
        priority = addFile(queue, "backend/src/main/java/com/dropai/generated/GeneratedApplication.java", "后端生成", "SpringBoot启动类", List.of("backend/pom.xml"), priority);
        priority = addFile(queue, "backend/src/main/java/com/dropai/generated/common/Result.java", "后端生成", "统一响应对象", List.of(), priority);
        priority = addFile(queue, "backend/src/main/java/com/dropai/generated/config/WebConfig.java", "后端生成", "Web与跨域配置", List.of(), priority);
        if (hasAiModules(plan)) {
            priority = addFile(queue, "backend/src/main/java/com/dropai/generated/config/AiModelConfig.java", "后端生成", "大模型配置，API Key从环境变量读取", List.of("backend/src/main/resources/application.yml"), priority);
            priority = addFile(queue, "backend/src/main/java/com/dropai/generated/service/AiAnalysisService.java", "后端生成", "AI分析服务接口", List.of("backend/src/main/java/com/dropai/generated/config/AiModelConfig.java"), priority);
            priority = addFile(queue, "backend/src/main/java/com/dropai/generated/service/impl/AiAnalysisServiceImpl.java", "后端生成", "AI错题与心理辅助分析实现", List.of("backend/src/main/java/com/dropai/generated/service/AiAnalysisService.java"), priority);
            priority = addFile(queue, "backend/src/main/java/com/dropai/generated/controller/AiChatController.java", "后端生成", "AI聊天接口与内容安全提示", List.of("backend/src/main/java/com/dropai/generated/service/AiAnalysisService.java"), priority);
        }
        for (TablePlan table : ensureSystemSetupTable(plan.tables())) {
            String name = className(table.name());
            priority = addFile(queue, "backend/src/main/java/com/dropai/generated/entity/" + name + ".java", "后端生成", table.comment() + "实体类", List.of("sql/schema.sql"), priority);
            priority = addFile(queue, "backend/src/main/java/com/dropai/generated/service/" + name + "Service.java", "后端生成", table.comment() + "业务接口", List.of("backend/src/main/java/com/dropai/generated/entity/" + name + ".java"), priority);
            priority = addFile(queue, "backend/src/main/java/com/dropai/generated/service/impl/" + name + "ServiceImpl.java", "后端生成", table.comment() + "业务实现", List.of("backend/src/main/java/com/dropai/generated/service/" + name + "Service.java"), priority);
            priority = addFile(queue, "backend/src/main/java/com/dropai/generated/controller/" + name + "Controller.java", "后端生成", table.comment() + "REST接口", List.of("backend/src/main/java/com/dropai/generated/service/" + name + "Service.java"), priority);
        }
        return priority;
    }

    private int buildFlaskQueue(List<FilePlan> queue, ComputerProjectPlan plan, int priority) {
        priority = addFile(queue, "backend/requirements.txt", "后端生成", "Flask依赖清单", List.of(), priority);
        priority = addFile(queue, "backend/config.py", "后端生成", "Flask配置", List.of("backend/requirements.txt"), priority);
        priority = addFile(queue, "backend/app.py", "后端生成", "Flask应用入口", List.of("backend/config.py"), priority);
        priority = addFile(queue, "backend/utils/response.py", "后端生成", "统一响应工具", List.of(), priority);
        priority = addFile(queue, "backend/models/__init__.py", "后端生成", "模型包初始化", List.of(), priority);
        for (TablePlan table : ensureSystemSetupTable(plan.tables())) {
            priority = addFile(queue, "backend/models/" + table.name() + ".py", "后端生成", table.comment() + "SQLAlchemy模型", List.of("sql/schema.sql"), priority);
            priority = addFile(queue, "backend/services/" + table.name() + "_service.py", "后端生成", table.comment() + "业务服务", List.of("backend/models/" + table.name() + ".py"), priority);
            priority = addFile(queue, "backend/routes/" + table.name() + "_routes.py", "后端生成", table.comment() + "Flask路由", List.of("backend/services/" + table.name() + "_service.py"), priority);
        }
        priority = addFile(queue, "backend/templates/index.html", "后端生成", "Flask模板首页", List.of("backend/app.py"), priority);
        return buildVueQueue(queue, plan, priority);
    }

    private int buildDjangoQueue(List<FilePlan> queue, ComputerProjectPlan plan, int priority) {
        priority = addFile(queue, "backend/requirements.txt", "后端生成", "Django依赖清单", List.of(), priority);
        priority = addFile(queue, "backend/manage.py", "后端生成", "Django管理入口", List.of("backend/requirements.txt"), priority);
        priority = addFile(queue, "backend/config/settings.py", "后端生成", "Django配置", List.of("backend/requirements.txt"), priority);
        priority = addFile(queue, "backend/config/urls.py", "后端生成", "Django总路由", List.of("backend/config/settings.py"), priority);
        priority = addFile(queue, "backend/config/wsgi.py", "后端生成", "WSGI入口", List.of("backend/config/settings.py"), priority);
        for (TablePlan table : ensureSystemSetupTable(plan.tables())) {
            String app = table.name();
            priority = addFile(queue, "backend/apps/" + app + "/models.py", "后端生成", table.comment() + "Django模型", List.of("sql/schema.sql"), priority);
            priority = addFile(queue, "backend/apps/" + app + "/serializers.py", "后端生成", table.comment() + "序列化器", List.of("backend/apps/" + app + "/models.py"), priority);
            priority = addFile(queue, "backend/apps/" + app + "/views.py", "后端生成", table.comment() + "视图接口", List.of("backend/apps/" + app + "/serializers.py"), priority);
            priority = addFile(queue, "backend/apps/" + app + "/urls.py", "后端生成", table.comment() + "应用路由", List.of("backend/apps/" + app + "/views.py"), priority);
        }
        return buildVueQueue(queue, plan, priority);
    }

    private int buildNodeQueue(List<FilePlan> queue, ComputerProjectPlan plan, int priority) {
        return buildVueQueue(queue, plan, buildNodeBackendQueue(queue, plan, priority));
    }

    private int buildNodeBackendQueue(List<FilePlan> queue, ComputerProjectPlan plan, int priority) {
        priority = addFile(queue, "backend/package.json", "后端生成", "Node依赖配置", List.of(), priority);
        priority = addFile(queue, "backend/config/database.js", "后端生成", "数据库配置", List.of("backend/package.json"), priority);
        priority = addFile(queue, "backend/utils/response.js", "后端生成", "统一响应工具", List.of(), priority);
        priority = addFile(queue, "backend/middleware/auth.js", "后端生成", "认证中间件", List.of(), priority);
        priority = addFile(queue, "backend/middleware/errorHandler.js", "后端生成", "错误处理中间件", List.of(), priority);
        for (TablePlan table : ensureSystemSetupTable(plan.tables())) {
            priority = addFile(queue, "backend/models/" + table.name() + ".js", "后端生成", table.comment() + "Sequelize模型", List.of("backend/config/database.js"), priority);
            priority = addFile(queue, "backend/services/" + table.name() + "Service.js", "后端生成", table.comment() + "业务服务", List.of("backend/models/" + table.name() + ".js"), priority);
            priority = addFile(queue, "backend/controllers/" + table.name() + "Controller.js", "后端生成", table.comment() + "控制器", List.of("backend/services/" + table.name() + "Service.js"), priority);
            priority = addFile(queue, "backend/routes/" + table.name() + "Routes.js", "后端生成", table.comment() + "路由", List.of("backend/controllers/" + table.name() + "Controller.js"), priority);
        }
        priority = addFile(queue, "backend/app.js", "后端生成", "Express应用入口", List.of("backend/package.json"), priority);
        return priority;
    }

    private int buildMiniprogramQueue(List<FilePlan> queue, ComputerProjectPlan plan, TechProfile tech, int priority) {
        priority = addFile(queue, "miniprogram/app.json", "小程序生成", "小程序全局配置", List.of(), priority);
        priority = addFile(queue, "miniprogram/app.js", "小程序生成", "小程序入口脚本", List.of("miniprogram/app.json"), priority);
        priority = addFile(queue, "miniprogram/app.wxss", "小程序生成", "小程序全局样式", List.of("miniprogram/app.json"), priority);
        priority = addFile(queue, "miniprogram/utils/request.js", "小程序生成", "小程序请求封装", List.of(), priority);
        priority = addFile(queue, "miniprogram/api/index.js", "小程序生成", "业务接口封装", List.of("miniprogram/utils/request.js"), priority);
        for (String page : List.of("login", "index", "business", "statistics", "mine")) {
            priority = addFile(queue, "miniprogram/pages/" + page + "/" + page + ".json", "小程序生成", page + "页面配置", List.of("miniprogram/app.json"), priority);
            priority = addFile(queue, "miniprogram/pages/" + page + "/" + page + ".wxml", "小程序生成", page + "页面结构", List.of("miniprogram/pages/" + page + "/" + page + ".json"), priority);
            priority = addFile(queue, "miniprogram/pages/" + page + "/" + page + ".js", "小程序生成", page + "页面逻辑", List.of("miniprogram/api/index.js"), priority);
            priority = addFile(queue, "miniprogram/pages/" + page + "/" + page + ".wxss", "小程序生成", page + "页面样式", List.of("miniprogram/pages/" + page + "/" + page + ".wxml"), priority);
        }
        return tech.backendStack().contains("Express") ? buildNodeBackendQueue(queue, plan, priority) : buildSpringBackendQueue(queue, plan, priority);
    }

    private int buildAnalysisQueue(List<FilePlan> queue, ComputerProjectPlan plan, int priority) {
        priority = addFile(queue, "analysis/requirements.txt", "分析生成", "Python分析依赖", List.of(), priority);
        priority = addFile(queue, "analysis/data/sample.csv", "分析生成", "示例数据集", List.of(), priority);
        priority = addFile(queue, "analysis/scripts/load_data.py", "分析生成", "数据读取脚本", List.of("analysis/data/sample.csv"), priority);
        priority = addFile(queue, "analysis/scripts/clean_data.py", "分析生成", "数据清洗脚本", List.of("analysis/scripts/load_data.py"), priority);
        priority = addFile(queue, "analysis/scripts/analyze.py", "分析生成", "统计分析脚本", List.of("analysis/scripts/clean_data.py"), priority);
        priority = addFile(queue, "analysis/scripts/build_charts.py", "分析生成", "图表生成脚本", List.of("analysis/scripts/analyze.py"), priority);
        priority = addFile(queue, "analysis/main.py", "分析生成", "分析主入口", List.of("analysis/scripts/build_charts.py"), priority);
        priority = addFile(queue, "analysis/notebooks/README.md", "分析生成", "Notebook说明", List.of("analysis/main.py"), priority);
        priority = addFile(queue, "analysis/charts/.gitkeep", "分析生成", "图表输出目录占位", List.of(), priority);
        priority = addFile(queue, "analysis/README.md", "分析生成", "分析工程说明", List.of("analysis/main.py"), priority);
        priority = addFile(queue, "dashboard/app.py", "预览构建", "Flask分析看板入口", List.of("analysis/main.py"), priority);
        priority = addFile(queue, "dashboard/templates/index.html", "预览构建", "分析看板页面", List.of("dashboard/app.py"), priority);
        return addFile(queue, "dashboard/static/style.css", "预览构建", "分析看板样式", List.of("dashboard/templates/index.html"), priority);
    }

    private int addFile(List<FilePlan> queue, String path, String stage, String responsibility, List<String> dependsOn, int priority) {
        if (queue.stream().anyMatch(file -> file.path().equals(path))) return priority;
        queue.add(new FilePlan(path, fileType(path), responsibility, dependsOn, priority, stage));
        return priority + 1;
    }

    private String generateValidatedFile(ComputerProjectPlan plan, TechProfile tech, FilePlan file, List<FileSummary> summaries) {
        String fallback = fallbackFileContent(plan, tech, file);
        for (int attempt = 0; attempt < 3; attempt++) {
            String content = attempt == 0 ? generateFileWithMatrix(plan, tech, file, summaries) : fallback;
            if (content == null || content.isBlank()) content = fallback;
            if (validateFile(file, content, plan)) return content;
        }
        throw new IllegalStateException(file.path() + " 校验失败");
    }

    private String generateFileWithMatrix(ComputerProjectPlan plan, TechProfile tech, FilePlan file, List<FileSummary> summaries) {
        if (!matrixDesignService.apiKeyConfigured()) return "";
        String instructions = """
                你是全栈工程师。只生成当前文件的完整内容，不要解释，不要 Markdown 代码围栏。
                生成内容必须是毕业设计/课程设计/管理系统/数据分析系统的合法合规代码。
                不得生成安全测试、漏洞利用、扫描、爆破、绕过认证、代理池、未授权数据采集等内容。
                代码必须可运行，并与给定语言、框架、数据库表、接口约定和命名规范一致。
                """;
        String input = """
                项目题目：%s
                项目类型：%s
                编程语言：%s
                后端技术栈：%s
                前端技术栈：%s
                数据库：%s
                是否小程序：%s
                是否桌面端：%s
                是否数据分析：%s
                用户角色：%s
                功能模块：%s
                数据库表：%s
                后端接口：%s
                当前文件路径：%s
                当前文件类型：%s
                当前文件职责：%s
                依赖文件：%s
                已生成文件摘要：%s
                """.formatted(plan.title(), tech.projectType(), tech.language(), tech.backendStack(), tech.frontendStack(),
                tech.databaseType(), tech.needMiniprogram(), tech.needDesktop(), tech.needDataAnalysis(),
                plan.roles(), plan.modules(), plan.tables(), plan.apis(), file.path(), file.type(),
                file.responsibility(), file.dependsOn(), summaries);
        try {
            return matrixDesignService.generate(instructions, input).replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```$", "").trim();
        } catch (Exception exception) {
            return "";
        }
    }

    private String fallbackFileContent(ComputerProjectPlan plan, TechProfile tech, FilePlan file) {
        String path = file.path();
        if ("sql/schema.sql".equals(path)) return schemaSql(plan);
        if ("sql/data.sql".equals(path)) return "INSERT INTO system_setup(setup_key,setup_value,status) VALUES ('admin_password_policy','FIRST_RUN_SET_PASSWORD','PENDING');\n";
        if ("sql/init.sql".equals(path)) return "SOURCE schema.sql;\nSOURCE data.sql;\n";
        if (path.endsWith("application.yml")) return "server:\n  port: 8080\nspring:\n  datasource:\n    url: jdbc:mysql://localhost:3306/dropai_generated?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai\n    username: root\n    password: ${DB_PASSWORD:}\n  jackson:\n    time-zone: Asia/Shanghai\nmybatis-plus:\n  mapper-locations: classpath*:mapper/*.xml\nai:\n  provider: openai-compatible\n  api-key: ${OPENAI_API_KEY:}\n  safety-notice: AI分析仅用于学习辅助与心理状态参考，不替代专业诊断或治疗建议。\n";
        if (path.endsWith("Result.java")) return "package com.dropai.generated.common;\n\npublic record Result<T>(int code, String message, T data) {\n    public static <T> Result<T> ok(T data) { return new Result<>(200, \"success\", data); }\n    public static <T> Result<T> fail(String message) { return new Result<>(500, message, null); }\n}\n";
        if (path.endsWith("PageResult.java")) return "package com.dropai.generated.common;\n\nimport java.util.List;\n\npublic record PageResult<T>(long total, List<T> records) {}\n";
        if (path.endsWith("BusinessException.java")) return "package com.dropai.generated.common;\n\npublic class BusinessException extends RuntimeException {\n    public BusinessException(String message) { super(message); }\n}\n";
        if (path.endsWith("LoginRequest.java")) return "package com.dropai.generated.dto;\n\npublic record LoginRequest(String username, String password) {}\n";
        if (path.endsWith("PageQuery.java")) return "package com.dropai.generated.dto;\n\npublic record PageQuery(int pageNum, int pageSize, String keyword) {}\n";
        if (path.endsWith("CorsConfig.java")) return "package com.dropai.generated.config;\n\nimport org.springframework.context.annotation.Configuration;\n\n@Configuration\npublic class CorsConfig {}\n";
        if (path.endsWith("MyBatisPlusConfig.java")) return "package com.dropai.generated.config;\n\nimport org.springframework.context.annotation.Configuration;\n\n@Configuration\npublic class MyBatisPlusConfig {}\n";
        if (path.endsWith("AiModelConfig.java")) return aiModelConfig();
        if (path.endsWith("AiAnalysisService.java")) return "package com.dropai.generated.service;\n\npublic interface AiAnalysisService {\n    String analyzeWrongQuestion(String question, String answer);\n    String analyzePsychologicalState(String text);\n    String chat(String message);\n}\n";
        if (path.endsWith("AiAnalysisServiceImpl.java")) return aiAnalysisServiceImpl();
        if (path.endsWith("AiChatController.java")) return aiChatController();
        if (path.endsWith("Mapper.xml")) return mapperXml(path);
        if (path.endsWith("pom.xml")) return backendPom();
        if (path.endsWith("GeneratedApplication.java")) return "package com.dropai.generated;\n\nimport org.springframework.boot.SpringApplication;\nimport org.springframework.boot.autoconfigure.SpringBootApplication;\n\n@SpringBootApplication\npublic class GeneratedApplication {\n    public static void main(String[] args) { SpringApplication.run(GeneratedApplication.class, args); }\n}\n";
        if (path.endsWith("WebConfig.java")) return "package com.dropai.generated.config;\n\nimport org.springframework.context.annotation.Configuration;\nimport org.springframework.web.servlet.config.annotation.CorsRegistry;\nimport org.springframework.web.servlet.config.annotation.WebMvcConfigurer;\n\n@Configuration\npublic class WebConfig implements WebMvcConfigurer {\n    @Override public void addCorsMappings(CorsRegistry registry) { registry.addMapping(\"/api/**\").allowedOrigins(\"*\").allowedMethods(\"GET\",\"POST\",\"PUT\",\"DELETE\"); }\n}\n";
        if (path.contains("/entity/")) return javaEntity(path, plan);
        if (path.contains("/mapper/")) return javaMapper(path);
        if (path.contains("/service/impl/")) return javaServiceImpl(path);
        if (path.contains("/service/")) return javaService(path);
        if (path.contains("/controller/")) return javaController(path);
        if (path.endsWith("requirements.txt")) return pythonRequirements(path, tech);
        if (path.endsWith("config.py")) return "import os\n\nDATABASE_URL = os.getenv('DATABASE_URL', 'sqlite:///dropai_generated.db')\nSECRET_KEY = os.getenv('SECRET_KEY', 'change-me-on-first-run')\n";
        if (path.endsWith("app.py") && path.startsWith("backend/")) return flaskApp(plan);
        if (path.contains("backend/models/") && path.endsWith(".py")) return pythonModel(path, plan);
        if (path.contains("backend/services/") && path.endsWith(".py")) return pythonService(path);
        if (path.contains("backend/routes/") && path.endsWith(".py")) return pythonRoute(path);
        if (path.endsWith("utils/response.py")) return "def ok(data=None, message='success'):\n    return {'code': 200, 'message': message, 'data': data}\n\ndef fail(message):\n    return {'code': 500, 'message': message, 'data': None}\n";
        if (path.endsWith("manage.py")) return "#!/usr/bin/env python\nimport os\nimport sys\n\nif __name__ == '__main__':\n    os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'config.settings')\n    from django.core.management import execute_from_command_line\n    execute_from_command_line(sys.argv)\n";
        if (path.endsWith("settings.py")) return "SECRET_KEY='first-run-change-me'\nDEBUG=True\nROOT_URLCONF='config.urls'\nINSTALLED_APPS=['django.contrib.contenttypes','rest_framework']\nDATABASES={'default':{'ENGINE':'django.db.backends.sqlite3','NAME':'db.sqlite3'}}\nDEFAULT_AUTO_FIELD='django.db.models.BigAutoField'\n";
        if (path.endsWith("wsgi.py")) return "import os\nfrom django.core.wsgi import get_wsgi_application\nos.environ.setdefault('DJANGO_SETTINGS_MODULE','config.settings')\napplication=get_wsgi_application()\n";
        if (path.endsWith("urls.py") && path.contains("backend/config/")) return "from django.urls import path\nurlpatterns=[]\n";
        if (path.contains("backend/apps/") && path.endsWith("models.py")) return djangoModels(path, plan);
        if (path.contains("backend/apps/") && path.endsWith("serializers.py")) return "from rest_framework import serializers\n\nclass GeneratedSerializer(serializers.Serializer):\n    id = serializers.IntegerField(required=False)\n    name = serializers.CharField(required=False)\n    status = serializers.CharField(required=False)\n";
        if (path.contains("backend/apps/") && path.endsWith("views.py")) return "from rest_framework.views import APIView\nfrom rest_framework.response import Response\n\nclass ListCreateView(APIView):\n    def get(self, request):\n        return Response({'code': 200, 'data': []})\n    def post(self, request):\n        return Response({'code': 200, 'data': request.data})\n";
        if (path.contains("backend/apps/") && path.endsWith("urls.py")) return "from django.urls import path\nfrom .views import ListCreateView\nurlpatterns=[path('', ListCreateView.as_view())]\n";
        if (path.equals("backend/package.json")) return "{\"scripts\":{\"dev\":\"node app.js\",\"start\":\"node app.js\"},\"dependencies\":{\"cors\":\"latest\",\"express\":\"latest\",\"sequelize\":\"latest\",\"sqlite3\":\"latest\"},\"devDependencies\":{}}\n";
        if (path.endsWith("database.js")) return "const { Sequelize } = require('sequelize')\nmodule.exports = new Sequelize({ dialect: 'sqlite', storage: './dropai_generated.sqlite', logging: false })\n";
        if (path.endsWith("response.js")) return "exports.ok = (data) => ({ code: 200, message: 'success', data })\nexports.fail = (message) => ({ code: 500, message, data: null })\n";
        if (path.endsWith("auth.js")) return "module.exports = (req, res, next) => { next() }\n";
        if (path.endsWith("errorHandler.js")) return "module.exports = (err, req, res, next) => { res.status(500).json({ code: 500, message: err.message }) }\n";
        if (path.contains("backend/models/") && path.endsWith(".js")) return nodeModel(path);
        if (path.contains("backend/services/") && path.endsWith(".js")) return nodeService(path);
        if (path.contains("backend/controllers/") && path.endsWith(".js")) return nodeController(path);
        if (path.contains("backend/routes/") && path.endsWith(".js")) return nodeRoute(path);
        if (path.equals("backend/app.js")) return nodeApp(plan);
        if (path.startsWith("miniprogram/")) return miniprogramFile(path, plan);
        if (path.startsWith("analysis/") || path.startsWith("dashboard/")) return analysisFile(path, plan);
        if ("frontend/package.json".equals(path)) return "{\"scripts\":{\"dev\":\"vite --host 0.0.0.0\",\"build\":\"vite build\"},\"dependencies\":{\"@vitejs/plugin-vue\":\"latest\",\"vite\":\"latest\",\"vue\":\"latest\",\"vue-router\":\"latest\",\"axios\":\"latest\",\"element-plus\":\"latest\"},\"devDependencies\":{}}\n";
        if ("frontend/vite.config.js".equals(path)) return "import { defineConfig } from 'vite'\nimport vue from '@vitejs/plugin-vue'\nexport default defineConfig({ plugins: [vue()], server: { port: 5173 } })\n";
        if ("frontend/index.html".equals(path)) return "<div id=\"app\"></div><script type=\"module\" src=\"/src/main.js\"></script>\n";
        if ("frontend/src/api/request.js".equals(path)) return "import axios from 'axios'\nexport const request = axios.create({ baseURL: '/api', timeout: 15000 })\nexport const listRecords = (name) => request.get(`/${name}`)\n";
        if (path.startsWith("frontend/src/api/") && path.endsWith(".js")) {
            String name = path.substring(path.lastIndexOf('/') + 1).replace(".js", "");
            String api = name.replace("_", "-");
            return "import { request } from './request'\nexport const list = () => request.get('/" + api + "')\nexport const save = (data) => request.post('/" + api + "', data)\n";
        }
        if ("frontend/src/router/index.js".equals(path)) return "import { createRouter, createWebHistory } from 'vue-router'\nimport Login from '../views/Login.vue'\nimport Dashboard from '../views/Dashboard.vue'\nimport Business from '../views/Business.vue'\nimport Statistics from '../views/Statistics.vue'\nimport UserManage from '../views/UserManage.vue'\nexport default createRouter({ history: createWebHistory(), routes: [{path:'/',redirect:'/dashboard'},{path:'/login',component:Login},{path:'/dashboard',component:Dashboard},{path:'/business',component:Business},{path:'/statistics',component:Statistics},{path:'/users',component:UserManage}] })\n";
        if (path.endsWith("DataCard.vue")) return vue("DataCard", "<article class=\"card\"><strong>{{ title }}</strong><span>{{ value }}</span></article>", "const props = defineProps({ title: String, value: [String, Number] })");
        if (path.endsWith("Login.vue")) return vue("Login", "<main class=\"page\"><h1>" + plan.title() + "</h1><p>首次运行时创建管理员账户并设置强密码。</p><input placeholder=\"手机号\"/><input placeholder=\"密码\" type=\"password\"/><button>登录</button></main>", "");
        if (path.endsWith("Dashboard.vue")) return vue("Dashboard", "<main class=\"page\"><h1>首页仪表盘</h1><section class=\"grid\"><DataCard title=\"用户\" value=\"128\"/><DataCard title=\"待办\" value=\"16\"/><DataCard title=\"通知\" value=\"9\"/></section></main>", "import DataCard from '../components/DataCard.vue'");
        if (path.endsWith("Business.vue")) return vue("Business", "<main class=\"page\"><h1>核心业务管理</h1><ul><li v-for=\"item in modules\" :key=\"item\">{{ item }}</li></ul></main>", "const modules = " + toJsArray(plan.modules()));
        if (path.endsWith("Statistics.vue")) return vue("Statistics", "<main class=\"page\"><h1>数据统计</h1><p>按月份、状态和业务类型展示趋势分析。</p></main>", "");
        if (path.endsWith("UserManage.vue")) return vue("UserManage", "<main class=\"page\"><h1>用户管理</h1><p>角色：" + String.join("、", plan.roles()) + "</p></main>", "");
        if (path.startsWith("frontend/src/views/") && path.endsWith("Manage.vue")) {
            String name = path.substring(path.lastIndexOf('/') + 1).replace(".vue", "");
            return vue(name, "<main class=\"page\"><h1>" + name + "</h1><p>支持分页查询、新增、编辑、状态维护和导出。</p></main>", "");
        }
        if (path.endsWith("App.vue")) return "<template><router-view /></template>\n<script setup></script>\n<style>body{margin:0;font-family:Arial,'Microsoft YaHei',sans-serif;background:#f5f7fb}.page{padding:32px}.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}.card{display:block;background:white;border:1px solid #dbe5f4;border-radius:8px;padding:18px}button{background:#2563eb;color:white;border:0;border-radius:6px;padding:10px 18px}</style>\n";
        if (path.endsWith("main.js")) return "import { createApp } from 'vue'\nimport ElementPlus from 'element-plus'\nimport 'element-plus/dist/index.css'\nimport App from './App.vue'\nimport router from './router'\ncreateApp(App).use(router).use(ElementPlus).mount('#app')\n";
        if ("README.md".equals(path)) return readme(plan, tech);
        if (path.endsWith("论文大纲.md")) return "# 论文大纲\n\n" + String.join("\n", plan.paperOutline().stream().map(item -> "- " + item).toList()) + "\n";
        if (path.endsWith("毕业论文.docx")) return "本文件为可由 Word 打开的毕业论文内容草稿。\n\n题目：" + plan.title() + "\n\n" + String.join("\n\n", plan.paperOutline()) + "\n";
        return file.responsibility() + "\n";
    }

    private boolean validateFile(FilePlan file, String content, ComputerProjectPlan plan) {
        if (content == null || content.trim().length() < 12) return false;
        String trimmed = content.trim().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("说明") || trimmed.startsWith("以下") || trimmed.contains("伪代码")) return false;
        String path = file.path();
        if (!file.type().equals(fileType(path))) return false;
        if (path.endsWith(".sql") && path.endsWith("schema.sql")) return content.toLowerCase(Locale.ROOT).contains("create table");
        if (path.endsWith("data.sql")) return content.toLowerCase(Locale.ROOT).contains("insert");
        if (path.endsWith("init.sql")) return content.toLowerCase(Locale.ROOT).contains("schema.sql");
        if (path.endsWith(".vue")) return content.contains("<template") && content.contains("<script");
        if (path.contains("/controller/")) return content.contains("@RestController") && content.contains("@RequestMapping");
        if (path.contains("/service/")) return content.contains("list") || content.contains("save") || content.contains("Service");
        if (path.contains("/entity/")) return content.contains("class ") && ensureSystemSetupTable(plan.tables()).stream().anyMatch(table -> content.contains(className(table.name())));
        if (path.endsWith(".java")) return content.contains("package com.dropai.generated");
        if (path.endsWith(".py")) return content.contains("def ") || content.contains("class ") || content.contains("import ");
        if (path.endsWith("requirements.txt")) return content.contains("\n");
        if (path.endsWith(".js")) return content.contains("module.exports") || content.contains("export ") || content.contains("import ") || content.contains("Page(") || content.contains("App(") || content.contains("require(");
        if (path.endsWith(".json")) return content.trim().startsWith("{");
        if (path.endsWith(".wxml")) return content.contains("<view") || content.contains("<text");
        if (path.endsWith(".html")) return content.contains("<");
        if (path.endsWith("README.md")) return content.contains("运行") && content.contains("目录");
        return true;
    }

    private String schemaSql(ComputerProjectPlan plan) {
        StringBuilder sql = new StringBuilder("CREATE DATABASE IF NOT EXISTS dropai_generated DEFAULT CHARSET utf8mb4;\nUSE dropai_generated;\n\n");
        for (TablePlan table : ensureSystemSetupTable(plan.tables())) {
            sql.append("CREATE TABLE IF NOT EXISTS ").append(table.name()).append(" (\n");
            List<String> fields = normalizeFields(table.fields());
            for (int i = 0; i < fields.size(); i++) {
                String field = fields.get(i);
                String suffix = i == fields.size() - 1 ? "\n" : ",\n";
                if ("id".equals(field)) sql.append("  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键'").append(suffix);
                else if (field.endsWith("_at")) sql.append("  ").append(field).append(" DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '时间字段'").append(suffix);
                else if (field.endsWith("_id")) sql.append("  ").append(field).append(" BIGINT COMMENT '关联ID'").append(suffix);
                else sql.append("  ").append(field).append(" VARCHAR(255) COMMENT '").append(field).append("'").append(suffix);
            }
            sql.append(") COMMENT='").append(table.comment()).append("';\n\n");
        }
        return sql.toString();
    }

    private String backendPom() {
        return """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.dropai</groupId><artifactId>generated-project</artifactId><version>1.0.0</version>
                  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.3.5</version></parent>
                  <properties><java.version>17</java.version></properties>
                  <dependencies>
                    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
                    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
                    <dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId><scope>runtime</scope></dependency>
                  </dependencies>
                </project>
                """;
    }

    private String javaEntity(String path, ComputerProjectPlan plan) {
        String name = path.substring(path.lastIndexOf('/') + 1).replace(".java", "");
        TablePlan table = ensureSystemSetupTable(plan.tables()).stream().filter(item -> className(item.name()).equals(name)).findFirst().orElse(plan.tables().get(0));
        StringBuilder code = new StringBuilder("package com.dropai.generated.entity;\n\npublic class " + name + " {\n");
        for (String field : normalizeFields(table.fields())) code.append("    private String ").append(camel(field)).append(";\n");
        code.append("}\n");
        return code.toString();
    }

    private String javaMapper(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).replace("Mapper.java", "");
        return "package com.dropai.generated.mapper;\n\nimport com.dropai.generated.entity." + name + ";\nimport java.util.*;\n\npublic interface " + name + "Mapper {\n    List<" + name + "> list();\n    int insert(" + name + " entity);\n}\n";
    }

    private String javaService(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).replace("Service.java", "");
        return "package com.dropai.generated.service;\n\nimport com.dropai.generated.entity." + name + ";\nimport java.util.*;\n\npublic interface " + name + "Service {\n    List<" + name + "> list();\n    " + name + " save(" + name + " entity);\n}\n";
    }

    private String javaServiceImpl(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).replace("ServiceImpl.java", "");
        return "package com.dropai.generated.service.impl;\n\nimport com.dropai.generated.entity." + name + ";\nimport com.dropai.generated.service." + name + "Service;\nimport org.springframework.stereotype.Service;\nimport java.util.*;\n\n@Service\npublic class " + name + "ServiceImpl implements " + name + "Service {\n    private final List<" + name + "> data = new ArrayList<>();\n    public List<" + name + "> list(){ return data; }\n    public " + name + " save(" + name + " entity){ data.add(entity); return entity; }\n}\n";
    }

    private String javaController(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).replace("Controller.java", "");
        String slug = name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
        return "package com.dropai.generated.controller;\n\nimport com.dropai.generated.entity." + name + ";\nimport com.dropai.generated.service." + name + "Service;\nimport org.springframework.web.bind.annotation.*;\nimport java.util.*;\n\n@RestController\n@RequestMapping(\"/api/" + slug + "\")\npublic class " + name + "Controller {\n    private final " + name + "Service service;\n    public " + name + "Controller(" + name + "Service service){ this.service = service; }\n    @GetMapping public List<" + name + "> list(){ return service.list(); }\n    @PostMapping public " + name + " save(@RequestBody " + name + " entity){ return service.save(entity); }\n}\n";
    }

    private String aiModelConfig() {
        return "package com.dropai.generated.config;\n\nimport org.springframework.beans.factory.annotation.Value;\nimport org.springframework.context.annotation.Configuration;\n\n@Configuration\npublic class AiModelConfig {\n    @Value(\"${ai.api-key:${OPENAI_API_KEY:}}\")\n    private String apiKey;\n\n    @Value(\"${ai.safety-notice:AI分析仅用于学习辅助与心理状态参考，不替代专业诊断或治疗建议。}\")\n    private String safetyNotice;\n\n    public String getApiKey() { return apiKey; }\n    public String getSafetyNotice() { return safetyNotice; }\n}\n";
    }

    private String aiAnalysisServiceImpl() {
        return "package com.dropai.generated.service.impl;\n\nimport com.dropai.generated.config.AiModelConfig;\nimport com.dropai.generated.service.AiAnalysisService;\nimport org.springframework.stereotype.Service;\n\n@Service\npublic class AiAnalysisServiceImpl implements AiAnalysisService {\n    private final AiModelConfig config;\n    public AiAnalysisServiceImpl(AiModelConfig config) { this.config = config; }\n\n    @Override\n    public String analyzeWrongQuestion(String question, String answer) {\n        return config.getSafetyNotice() + \" 当前结果仅帮助学生理解错因、归纳知识点并给出练习建议。\";\n    }\n\n    @Override\n    public String analyzePsychologicalState(String text) {\n        return config.getSafetyNotice() + \" 本功能只做学习压力和心理状态辅助观察，不输出医学诊断结论。\";\n    }\n\n    @Override\n    public String chat(String message) {\n        return config.getSafetyNotice() + \" 我会围绕学习规划、习题讲解和校园服务提供合规帮助。\";\n    }\n}\n";
    }

    private String aiChatController() {
        return "package com.dropai.generated.controller;\n\nimport com.dropai.generated.service.AiAnalysisService;\nimport org.springframework.web.bind.annotation.*;\nimport java.util.Map;\n\n@RestController\n@RequestMapping(\"/api/ai-chat\")\npublic class AiChatController {\n    private final AiAnalysisService aiAnalysisService;\n    public AiChatController(AiAnalysisService aiAnalysisService) { this.aiAnalysisService = aiAnalysisService; }\n\n    @PostMapping(\"/chat\")\n    public Map<String, String> chat(@RequestBody Map<String, String> body) {\n        return Map.of(\"reply\", aiAnalysisService.chat(body.getOrDefault(\"message\", \"\")));\n    }\n\n    @PostMapping(\"/wrong-question-analysis\")\n    public Map<String, String> wrongQuestion(@RequestBody Map<String, String> body) {\n        return Map.of(\"result\", aiAnalysisService.analyzeWrongQuestion(body.getOrDefault(\"question\", \"\"), body.getOrDefault(\"answer\", \"\")));\n    }\n\n    @PostMapping(\"/psychological-analysis\")\n    public Map<String, String> psychological(@RequestBody Map<String, String> body) {\n        return Map.of(\"result\", aiAnalysisService.analyzePsychologicalState(body.getOrDefault(\"text\", \"\")));\n    }\n}\n";
    }

    private String mapperXml(String path) {
        String mapper = path.substring(path.lastIndexOf('/') + 1).replace(".xml", "");
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"https://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n<mapper namespace=\"com.dropai.generated.mapper." + mapper + "\">\n</mapper>\n";
    }

    private String pythonRequirements(String path, TechProfile tech) {
        if (path.startsWith("analysis/")) return "pandas\nmatplotlib\nflask\n";
        if (tech.backendStack().contains("Django")) return "django\ndjangorestframework\nmysqlclient\n";
        return "flask\nflask-cors\nsqlalchemy\npymysql\n";
    }

    private String flaskApp(ComputerProjectPlan plan) {
        return "from flask import Flask, jsonify\nfrom flask_cors import CORS\n\napp = Flask(__name__)\nCORS(app)\n\n@app.get('/api/health')\ndef health():\n    return jsonify({'code': 200, 'data': {'project': '" + plan.title() + "', 'status': 'ok'}})\n\nif __name__ == '__main__':\n    app.run(host='0.0.0.0', port=5000, debug=True)\n";
    }

    private String pythonModel(String path, ComputerProjectPlan plan) {
        String table = path.substring(path.lastIndexOf('/') + 1).replace(".py", "");
        TablePlan tablePlan = tableByName(plan, table);
        StringBuilder code = new StringBuilder("from sqlalchemy import Column, Integer, String, DateTime\nfrom sqlalchemy.orm import declarative_base\nfrom datetime import datetime\n\nBase = declarative_base()\n\nclass " + className(table) + "(Base):\n    __tablename__ = '" + table + "'\n");
        for (String field : normalizeFields(tablePlan.fields())) {
            if ("id".equals(field)) code.append("    id = Column(Integer, primary_key=True, autoincrement=True)\n");
            else if (field.endsWith("_at")) code.append("    ").append(field).append(" = Column(DateTime, default=datetime.utcnow)\n");
            else code.append("    ").append(field).append(" = Column(String(255))\n");
        }
        return code.toString();
    }

    private String pythonService(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).replace("_service.py", "");
        return "def list_items():\n    return []\n\ndef create_item(payload):\n    payload = dict(payload or {})\n    payload.setdefault('status', 'ACTIVE')\n    payload.setdefault('module', '" + name + "')\n    return payload\n";
    }

    private String pythonRoute(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).replace("_routes.py", "");
        return "from flask import Blueprint, request\nfrom services." + name + "_service import list_items, create_item\nfrom utils.response import ok\n\nbp = Blueprint('" + name + "', __name__, url_prefix='/api/" + name.replace("_", "-") + "')\n\n@bp.get('')\ndef list_api():\n    return ok(list_items())\n\n@bp.post('')\ndef create_api():\n    return ok(create_item(request.json))\n";
    }

    private String djangoModels(String path, ComputerProjectPlan plan) {
        String table = path.split("/apps/")[1].split("/")[0];
        TablePlan tablePlan = tableByName(plan, table);
        StringBuilder code = new StringBuilder("from django.db import models\n\nclass " + className(table) + "(models.Model):\n");
        for (String field : normalizeFields(tablePlan.fields())) {
            if ("id".equals(field)) continue;
            if (field.endsWith("_at")) code.append("    ").append(field).append(" = models.DateTimeField(auto_now_add=True)\n");
            else code.append("    ").append(field).append(" = models.CharField(max_length=255, blank=True, default='')\n");
        }
        code.append("    class Meta:\n        db_table = '").append(table).append("'\n");
        return code.toString();
    }

    private String nodeModel(String path) {
        String table = path.substring(path.lastIndexOf('/') + 1).replace(".js", "");
        return "const { DataTypes } = require('sequelize')\nconst sequelize = require('../config/database')\nmodule.exports = sequelize.define('" + className(table) + "', { name: DataTypes.STRING, status: DataTypes.STRING, remark: DataTypes.STRING }, { tableName: '" + table + "', timestamps: false })\n";
    }

    private String nodeService(String path) {
        String table = path.substring(path.lastIndexOf('/') + 1).replace("Service.js", "");
        return "exports.list = async () => []\nexports.create = async (payload) => ({ ...payload, status: payload.status || 'ACTIVE', module: '" + table + "' })\n";
    }

    private String nodeController(String path) {
        String table = path.substring(path.lastIndexOf('/') + 1).replace("Controller.js", "");
        return "const service = require('../services/" + table + "Service')\nexports.list = async (req, res, next) => { try { res.json({ code: 200, data: await service.list() }) } catch (e) { next(e) } }\nexports.create = async (req, res, next) => { try { res.json({ code: 200, data: await service.create(req.body) }) } catch (e) { next(e) } }\n";
    }

    private String nodeRoute(String path) {
        String table = path.substring(path.lastIndexOf('/') + 1).replace("Routes.js", "");
        return "const router = require('express').Router()\nconst controller = require('../controllers/" + table + "Controller')\nrouter.get('/', controller.list)\nrouter.post('/', controller.create)\nmodule.exports = router\n";
    }

    private String nodeApp(ComputerProjectPlan plan) {
        StringBuilder code = new StringBuilder("const express = require('express')\nconst cors = require('cors')\nconst errorHandler = require('./middleware/errorHandler')\nconst app = express()\napp.use(cors())\napp.use(express.json())\napp.get('/api/health', (req, res) => res.json({ code: 200, data: '" + plan.title() + "' }))\n");
        for (TablePlan table : ensureSystemSetupTable(plan.tables())) {
            code.append("app.use('/api/").append(table.name().replace("_", "-")).append("', require('./routes/").append(table.name()).append("Routes'))\n");
        }
        code.append("app.use(errorHandler)\napp.listen(3000, () => console.log('server started on 3000'))\n");
        return code.toString();
    }

    private String miniprogramFile(String path, ComputerProjectPlan plan) {
        if (path.endsWith("app.json")) return "{\"pages\":[\"pages/login/login\",\"pages/index/index\",\"pages/business/business\",\"pages/statistics/statistics\",\"pages/mine/mine\"],\"window\":{\"navigationBarTitleText\":\"" + plan.title() + "\"}}\n";
        if (path.endsWith("app.js")) return "App({ globalData: { baseUrl: 'http://localhost:8080/api' } })\n";
        if (path.endsWith("app.wxss")) return "page{background:#f6f8fb;color:#172033}.card{background:#fff;margin:20rpx;padding:24rpx;border-radius:12rpx}\n";
        if (path.endsWith("utils/request.js")) return "const request = (url, data = {}, method = 'GET') => new Promise((resolve, reject) => wx.request({ url: getApp().globalData.baseUrl + url, data, method, success: resolve, fail: reject }))\nmodule.exports = { request }\n";
        if (path.endsWith("api/index.js")) return "const { request } = require('../utils/request')\nmodule.exports = { listBusiness: () => request('/business-record') }\n";
        if (path.endsWith(".json")) return "{\"navigationBarTitleText\":\"" + plan.title() + "\"}\n";
        if (path.endsWith(".wxml")) return "<view class=\"card\"><text>" + plan.title() + "</text><view>" + plan.domain() + "</view></view>\n";
        if (path.endsWith(".wxss")) return ".card{background:#fff;margin:24rpx;padding:24rpx;border-radius:12rpx}text{font-weight:bold}\n";
        return "Page({ data: { title: '" + plan.title() + "', modules: " + toJsArray(plan.modules()) + " }, onLoad() {} })\n";
    }

    private String analysisFile(String path, ComputerProjectPlan plan) {
        if (path.endsWith("requirements.txt")) return "pandas\nmatplotlib\nflask\n";
        if (path.endsWith("sample.csv")) return "month,category,amount,status\n2026-01,A,1200,done\n2026-02,B,1680,done\n";
        if (path.endsWith("load_data.py")) return "import pandas as pd\n\ndef load_data(path='analysis/data/sample.csv'):\n    return pd.read_csv(path)\n";
        if (path.endsWith("clean_data.py")) return "def clean_data(df):\n    return df.dropna().copy()\n";
        if (path.endsWith("analyze.py")) return "def summarize(df):\n    return df.groupby('category')['amount'].sum().reset_index()\n";
        if (path.endsWith("build_charts.py")) return "import matplotlib.pyplot as plt\n\ndef build_chart(summary, output='analysis/charts/summary.png'):\n    summary.plot(kind='bar', x='category', y='amount')\n    plt.tight_layout()\n    plt.savefig(output)\n";
        if (path.endsWith("analysis/main.py")) return "from scripts.load_data import load_data\nfrom scripts.clean_data import clean_data\nfrom scripts.analyze import summarize\nfrom scripts.build_charts import build_chart\n\ndf = clean_data(load_data())\nsummary = summarize(df)\nbuild_chart(summary)\nprint(summary)\n";
        if (path.endsWith("dashboard/app.py")) return "from flask import Flask, render_template\napp = Flask(__name__)\n@app.get('/')\ndef index():\n    return render_template('index.html', title='" + plan.title() + "')\nif __name__ == '__main__':\n    app.run(port=5000, debug=True)\n";
        if (path.endsWith("index.html")) return "<!doctype html><html><head><link rel=\"stylesheet\" href=\"/static/style.css\"></head><body><h1>{{ title }}</h1><section>数据分析看板</section></body></html>\n";
        if (path.endsWith("style.css")) return "body{font-family:Arial,'Microsoft YaHei',sans-serif;background:#f6f8fb;color:#172033;padding:32px}section{background:#fff;border:1px solid #dbe5f4;border-radius:8px;padding:24px}\n";
        return "# " + plan.title() + " 数据分析工程\n\n运行：python analysis/main.py\n";
    }

    private String readme(ComputerProjectPlan plan, TechProfile tech) {
        return "# " + plan.title() + "\n\n## 项目简介\n" + plan.domain() + "毕业设计项目。\n\n## 技术栈\n- 编程语言：" + tech.language() + "\n- 后端：" + tech.backendStack() + "\n- 前端：" + tech.frontendStack() + "\n- 数据库：" + tech.databaseType() + "\n\n## 目录说明\n- backend/miniprogram/frontend/analysis/dashboard：按识别技术栈生成的运行工程\n- sql：数据库初始化脚本\n- paper：论文大纲与论文内容\n- preview：静态网页预览\n\n## 运行说明\n1. 按需导入 sql/schema.sql 和 sql/data.sql。\n2. 根据 backend、frontend、miniprogram 或 analysis 目录内文件启动对应工程。\n3. 网页预览可直接打开 preview 下的 index.html。\n\n## 安全说明\n系统首次启动时创建管理员账户并设置强密码，不提供默认弱密码。\n";
    }

    private TablePlan tableByName(ComputerProjectPlan plan, String tableName) {
        return ensureSystemSetupTable(plan.tables()).stream()
                .filter(table -> table.name().equalsIgnoreCase(tableName))
                .findFirst()
                .orElseGet(() -> new TablePlan(tableName, tableName, List.of("id", "name", "status", "created_at")));
    }

    private ComputerGenerationJob requireOwnedJob(String jobId) {
        ComputerGenerationJob job = jobMapper.selectById(jobId);
        Long userId = AuthContext.requireUserId();
        if (job == null || !userId.equals(job.getUserId())) throw new IllegalArgumentException("生成任务不存在");
        return job;
    }

    private Resource safeResource(Path target) {
        Path normalized = target.toAbsolutePath().normalize();
        assertUnderRoot(normalized);
        if (!Files.isRegularFile(normalized)) throw new IllegalArgumentException("文件不存在");
        return new FileSystemResource(normalized);
    }

    private void updateStage(ComputerGenerationJob job, int index, String status, String error) {
        job.setCurrentStage(STAGES.get(Math.min(index, STAGES.size() - 1)));
        job.setProgress(Math.min(100, Math.round((index + 1) * 100f / STAGES.size())));
        job.setStatus(status);
        job.setErrorMessage(error);
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateById(job);
    }

    private void updateStage(ComputerGenerationJob job, String stage, int progress, String currentFile) {
        job.setCurrentStage(stage);
        job.setCurrentFile(currentFile == null ? "" : currentFile);
        job.setProgress(Math.max(0, Math.min(100, progress)));
        job.setStatus("RUNNING");
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateById(job);
    }

    private String stageMessage(ComputerGenerationJob job) {
        return defaultText(job.getCurrentStage(), "等待开始");
    }

    private static Path write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private static void zip(Path sourceRoot, Path target) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(target)); var paths = Files.walk(sourceRoot)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(path -> !path.equals(target)).toList()) {
                out.putNextEntry(new ZipEntry(sourceRoot.relativize(path).toString().replace("\\", "/")));
                Files.copy(path, out);
                out.closeEntry();
            }
        }
    }

    private static void recreateDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.delete(path); } catch (IOException exception) { throw new UncheckedIOException(exception); }
                });
            }
        }
        Files.createDirectories(dir);
    }

    private static void assertUnderRoot(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(ROOT)) throw new IllegalArgumentException("非法文件路径");
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) if (text.contains(keyword)) return true;
        return false;
    }

    private static String safeSlug(String value) {
        String text = defaultText(value, "business");
        if (text.contains("用户")) return "users";
        if (text.contains("权限")) return "roles";
        if (text.contains("仪表")) return "dashboard";
        if (text.contains("消息")) return "notices";
        if (text.contains("个人")) return "profile";
        if (text.contains("宿舍") || text.contains("楼栋")) return "dormitories";
        if (text.contains("图书") || text.contains("借阅")) return "books";
        if (text.contains("商品") || text.contains("订单")) return "orders";
        if (text.contains("预约") || text.contains("排班")) return "reservations";
        if (text.contains("社团") || text.contains("活动")) return "clubs";
        if (text.contains("公开数据") || text.contains("数据处理")) return "datasets";
        if (text.contains("统计") || text.contains("分析")) return "analytics";
        return "business";
    }

    private static String domainSlug(String domain) {
        if (domain.contains("宿舍")) return "dormitory";
        if (domain.contains("图书")) return "library";
        if (domain.contains("商城")) return "mall";
        if (domain.contains("预约")) return "reservation";
        if (domain.contains("社团")) return "club";
        if (domain.contains("数据")) return "analytics";
        return "business";
    }

    private static String safeName(String value) {
        String result = defaultText(value, "project").replaceAll("[^\\p{IsHan}A-Za-z0-9_-]+", "-");
        return result.isBlank() ? "project" : result;
    }

    private static String safeClass(String value) {
        return "Generated" + Math.abs(defaultText(value, "Project").hashCode());
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static void ensureCompliantRequirement(String text) {
        String normalized = defaultText(text, "").toLowerCase(Locale.ROOT);
        for (String keyword : FORBIDDEN_REQUIREMENT_KEYWORDS) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(OUT_OF_SCOPE_MESSAGE);
            }
        }
    }

    private static String configToText(ComputerGenerationConfig config) {
        if (config == null) return "";
        StringBuilder text = new StringBuilder();
        text.append(defaultText(config.title(), "")).append('\n')
                .append(defaultText(config.projectType(), "")).append('\n')
                .append(defaultText(config.techStack(), "")).append('\n')
                .append(defaultText(config.programmingLanguage(), "")).append('\n')
                .append(defaultText(config.frontendStack(), "")).append('\n')
                .append(defaultText(config.backendStack(), "")).append('\n')
                .append(defaultText(config.databaseType(), "")).append('\n')
                .append(config.roles()).append('\n')
                .append(config.modules()).append('\n')
                .append(config.pages()).append('\n')
                .append(config.apis()).append('\n')
                .append(config.paperOutline()).append('\n');
        if (config.tables() != null) {
            for (TablePlanVO table : config.tables()) {
                text.append(table.name()).append(' ')
                        .append(table.comment()).append(' ')
                        .append(table.fields()).append('\n');
            }
        }
        return text.toString();
    }

    private static String extractTitle(String input, List<String> names) {
        String text = defaultText(input, "");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:题目|课题|项目名称)[:：\\s]*([^\\n。；;]{4,40})").matcher(text);
        if (matcher.find()) return matcher.group(1).trim();
        if (text.contains("宿舍")) return "学生宿舍管理系统";
        if (text.contains("图书")) return "图书管理系统";
        if (text.contains("商城")) return "在线商城系统";
        if (text.contains("预约")) return "预约管理系统";
        if (text.contains("社团")) return "社团管理系统";
        if (text.contains("公开数据") || text.contains("数据处理")) return "公开数据分析系统";
        if (!names.isEmpty()) return names.get(0).replaceAll("\\.[^.]+$", "").replace("任务书", "").replace("开题报告", "") + "系统";
        return "计算机毕业设计管理系统";
    }

    private static String recommendProjectType(ComputerProjectPlan plan, String input) {
        String text = (plan.domain() + input).toLowerCase(Locale.ROOT);
        if (containsAny(text, "微信", "小程序", "miniprogram", "wxapp")) return "微信小程序项目";
        if (containsAny(text, "node", "express", "koa", "api服务", "轻量web")) return "Node.js Express项目";
        if (containsAny(text, "django")) return "Django项目";
        if (containsAny(text, "flask")) return "Flask项目";
        if (containsAny(text, "streamlit", "dash", "pandas", "matplotlib", "数据分析", "可视化大屏", "财务分析", "销售分析", "教学数据", "物流数据", "公开数据")) return "Python数据分析项目";
        if (text.contains("python")) return "Python Web项目";
        return "Java Web项目";
    }

    private static String recommendTechStack(String projectType, String input) {
        String text = defaultText(input, "").toLowerCase(Locale.ROOT);
        if (projectType.contains("微信小程序")) return "微信原生小程序 + SpringBoot + MySQL";
        if (projectType.contains("Node")) return "Node.js + Express + Sequelize + SQLite";
        if (projectType.contains("Django")) return "Django + Django ORM + MySQL";
        if (projectType.contains("Flask")) return "Flask + SQLAlchemy + MySQL + Vue3";
        if (projectType.contains("Python数据分析")) return "Python + pandas + matplotlib + Flask + SQLite";
        if (projectType.contains("Python")) return text.contains("django") ? "Django + MySQL" : "Flask + Vue3 + MySQL";
        if (text.contains("thymeleaf")) return "Spring Boot 3.x + Thymeleaf + MySQL 8";
        return "Spring Boot 3.x + MyBatis-Plus + Vue3 + Element Plus + MySQL 8";
    }

    private static List<String> defaultPaperOutline() {
        return List.of("摘要", "Abstract", "绪论", "相关技术介绍", "系统需求分析", "系统总体设计", "数据库设计", "系统详细设计", "系统实现", "系统测试", "结论", "参考文献", "致谢");
    }

    private static List<String> nonEmpty(List<String> values, List<String> fallback) {
        return values == null || values.isEmpty() ? fallback : values;
    }

    private static String stripJson(String response) {
        String text = defaultText(response, "").trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : text;
    }

    private static List<String> readStringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) return values;
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isBlank()) values.add(value);
        }
        return values;
    }

    private static List<String> normalizeFields(List<String> fields) {
        List<String> result = new ArrayList<>();
        for (String field : fields) {
            String normalized = field.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_").replaceAll("_+", "_");
            if (!normalized.isBlank() && normalized.matches("[a-z][a-z0-9_]{0,60}")) {
                result.add(normalized);
            }
        }
        if (!result.contains("id")) result.add(0, "id");
        if (!result.contains("status")) result.add("status");
        if (!result.contains("created_at")) result.add("created_at");
        return result.stream().distinct().toList();
    }

    private static List<TablePlan> ensureSystemSetupTable(List<TablePlan> tables) {
        List<TablePlan> result = new ArrayList<>(tables == null ? List.of() : tables);
        boolean exists = result.stream().anyMatch(table -> "system_setup".equalsIgnoreCase(table.name()));
        if (!exists) {
            result.add(new TablePlan("system_setup", "系统初始化配置", List.of("id", "setup_key", "setup_value", "status", "created_at")));
        }
        return result;
    }

    private static String projectDirectoryTree(List<FilePlan> queue) {
        List<String> roots = queue.stream()
                .map(file -> file.path().contains("/") ? file.path().substring(0, file.path().indexOf('/')) + "/" : file.path())
                .distinct()
                .sorted()
                .toList();
        return "project/\n" +
                String.join("\n", roots.stream().map(root -> "├── " + root).toList()) +
                "\n\n文件队列：\n" +
                String.join("\n", queue.stream()
                        .map(file -> "- [" + file.priority() + "] " + file.path() + " (" + file.type() + ") - " + file.responsibility())
                        .toList());
    }

    private static String summarize(String content) {
        String value = defaultText(content, "").replaceAll("\\s+", " ").trim();
        return value.length() > 180 ? value.substring(0, 180) + "..." : value;
    }

    private static String vue(String name, String templateBody, String script) {
        return "<template>" + templateBody + "</template>\n<script setup>\n" + script + "\n</script>\n<style scoped>.page{padding:28px}.card{background:#fff;border:1px solid #dbe5f4;border-radius:8px;padding:16px;margin:10px}.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px}</style>\n";
    }

    private static String toJsArray(List<String> values) {
        return "[" + String.join(",", values.stream().map(value -> "'" + value.replace("'", "") + "'").toList()) + "]";
    }

    private static boolean hasAiModules(ComputerProjectPlan plan) {
        String text = (plan.domain() + " " + plan.modules() + " " + plan.tables()).toLowerCase(Locale.ROOT);
        return containsAny(text, "ai", "大模型", "错题", "聊天", "心理");
    }

    private static boolean hasPageForTable(List<String> pages, String tableName) {
        String expected = pageNameForTable(tableName);
        return pages.stream().map(ComputerGeneratorService::pageComponentName).anyMatch(expected::equals);
    }

    private static String pageNameForTable(String tableName) {
        return switch (tableName) {
            case "student_profile" -> "StudentManage";
            case "teacher_profile" -> "TeacherManage";
            case "exercise" -> "ExerciseManage";
            case "exercise_publish" -> "ExercisePublish";
            case "answer_record" -> "AnswerRecord";
            case "wrong_question_analysis" -> "WrongQuestionAnalysis";
            case "ai_chat_session", "ai_chat_message" -> "AiChat";
            case "psychological_analysis" -> "PsychologicalAnalysis";
            case "system_notice" -> "NoticeManage";
            case "sys_user" -> "UserManage";
            default -> className(tableName) + "Manage";
        };
    }

    private static String pageComponentName(String page) {
        String value = defaultText(page, "Business").trim();
        if (value.matches("[A-Z][A-Za-z0-9]*")) return value;
        if (containsAny(value, "登录")) return "Login";
        if (containsAny(value, "仪表盘", "首页")) return "Dashboard";
        if (containsAny(value, "学生")) return "StudentManage";
        if (containsAny(value, "教师")) return "TeacherManage";
        if (containsAny(value, "习题发布")) return "ExercisePublish";
        if (containsAny(value, "习题")) return "ExerciseManage";
        if (containsAny(value, "学生答题", "答题练习")) return "AnswerPractice";
        if (containsAny(value, "答题记录")) return "AnswerRecord";
        if (containsAny(value, "错题")) return "WrongQuestionAnalysis";
        if (containsAny(value, "AI聊天", "聊天")) return "AiChat";
        if (containsAny(value, "心理")) return "PsychologicalAnalysis";
        if (containsAny(value, "公告", "通知")) return "NoticeManage";
        if (containsAny(value, "统计")) return "Statistics";
        if (containsAny(value, "用户", "权限")) return "UserManage";
        return className(value.replace("页面", "").replace("管理", "")) + "Manage";
    }

    private static String fileType(String path) {
        String lower = defaultText(path, "").toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".vue")) return "vue";
        if (lower.endsWith(".sql")) return "sql";
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".js")) return "js";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".md")) return "md";
        if (lower.endsWith(".docx")) return "docx";
        if (lower.endsWith(".html")) return "html";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "yaml";
        if (lower.endsWith(".wxss")) return "wxss";
        if (lower.endsWith(".wxml")) return "wxml";
        if (lower.endsWith(".css")) return "css";
        if (lower.endsWith(".csv")) return "csv";
        if (lower.endsWith(".txt")) return "txt";
        return "text";
    }

    private static String className(String tableName) {
        StringBuilder result = new StringBuilder();
        for (String part : defaultText(tableName, "record").split("_")) {
            if (!part.isBlank()) result.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return result.isEmpty() ? "Record" : result.toString();
    }

    private static String camel(String field) {
        String name = className(field);
        return name.substring(0, 1).toLowerCase(Locale.ROOT) + name.substring(1);
    }

    private static String trimForModel(String value) {
        String text = defaultText(value, "");
        return text.length() > 10000 ? text.substring(0, 10000) : text;
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int value(Integer value) { return value == null ? 0 : value; }
    private static long value(Long value) { return value == null ? 0 : value; }
    private static String compact(String value) {
        if (value == null || value.isBlank()) return "无详细错误";
        return value.length() > 240 ? value.substring(0, 240) : value;
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record CreateComputerJobRequest(String title, String projectType, String techStack, String inputText,
                                           boolean generatePaper, boolean generateSql, boolean generateFrontend,
                                           boolean generateBackend, boolean generateAdmin, boolean generateUser,
                                           boolean generateTests, boolean generateReadme, boolean generateZip,
                                           boolean enablePreview) {}
    public record ComputerAnalyzeVO(ComputerJobVO job, ComputerPlanVO plan) {}
    public record ComputerPlanVO(String title, String projectType, String techStack, List<String> roles,
                                 List<String> modules, List<TablePlanVO> tables, List<String> pages,
                                 List<String> apis, List<String> paperOutline, String programmingLanguage,
                                 String frontendStack, String backendStack, String databaseType,
                                 boolean needMiniprogram, boolean needDesktop, boolean needDataAnalysis,
                                 String directoryTree, List<FilePlanVO> fileQueue, int pointsCost,
                                 boolean generatePaper, boolean generateTests, boolean enablePreview) {}
    public record ComputerGenerationConfig(String title, String projectType, String techStack, List<String> roles,
                                           String programmingLanguage, String frontendStack, String backendStack, String databaseType,
                                           boolean needMiniprogram, boolean needDesktop, boolean needDataAnalysis,
                                           List<String> modules, List<TablePlanVO> tables, List<String> pages,
                                           List<String> apis, List<String> paperOutline,
                                           boolean generatePaper, boolean generateTests, boolean enablePreview) {}
    public record TablePlanVO(String name, String comment, List<String> fields) {}
    public record FilePlanVO(String path, String type, String description, List<String> dependsOn, int priority) {
        static FilePlanVO from(FilePlan file) {
            return new FilePlanVO(file.path(), file.type(), file.responsibility(), file.dependsOn(), file.priority());
        }
    }
    public record ComputerJobVO(String id, String title, String projectType, String techStack, String status,
                                int progress, String currentStage, String currentFile, String errorMessage, int pointsCost,
                                String previewUrl, String downloadUrl, List<GeneratedFileVO> files,
                                String activePreviewUrl, List<String> stages) {}
    public record GeneratedFileVO(String fileType, String fileName, long fileSize, String downloadUrl) {}
    private record ComputerProjectPlan(String title, String domain, List<String> modules, List<String> roles,
                                       List<TablePlan> tables, List<String> pages, List<String> apis,
                                       List<String> paperOutline) {}
    private record TablePlan(String name, String comment, List<String> fields) {}
    private record DomainEntity(String name, String module, String table, String comment, List<String> fields,
                                String page, List<String> keywords) {}
    private record TechProfile(String projectType, String language, String backendStack, String frontendStack,
                               String databaseType, boolean needMiniprogram, boolean needDesktop,
                               boolean needDataAnalysis, String displayStack) {}
    private record FilePlan(String path, String type, String responsibility, List<String> dependsOn, int priority, String stage) {}
    private record FileSummary(String path, String summary) {}
}
