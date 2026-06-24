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

            List<FilePlan> queue = buildFileQueue(plan, job.getTechStack());
            updateStage(job, "目录生成", 4, "project/");
            int completed = 0;
            List<FileSummary> summaries = new ArrayList<>();
            for (FilePlan file : queue) {
                int progress = 5 + Math.round((completed * 78f) / Math.max(queue.size(), 1));
                updateStage(job, file.stage(), progress, file.path());
                String content = generateValidatedFile(plan, job.getTechStack(), file, summaries);
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
            updateStage(job, 1, "RUNNING", "");
            return matrixPlan;
        }
        String text = (job.getTitle() + "\n" + defaultText(job.getInputText(), "")).toLowerCase(Locale.ROOT);
        String domain = "通用业务管理";
        List<String> modules = new ArrayList<>(List.of("用户认证", "权限管理", "首页仪表盘", "消息中心", "个人中心"));
        List<String> roles = new ArrayList<>(List.of("管理员", "普通用户"));
        if (containsAny(text, "宿舍", "寝室", "公寓")) {
            domain = "学生宿舍管理";
            modules.addAll(List.of("楼栋管理", "宿舍分配", "入住登记", "报修工单", "卫生检查"));
            roles.addAll(List.of("学生", "宿舍管理员"));
        } else if (containsAny(text, "图书", "借阅", "书籍")) {
            domain = "图书管理";
            modules.addAll(List.of("图书档案", "借阅归还", "逾期提醒", "读者管理", "馆藏统计"));
            roles.addAll(List.of("读者", "图书管理员"));
        } else if (containsAny(text, "商城", "订单", "商品")) {
            domain = "在线商城";
            modules.addAll(List.of("商品管理", "购物车", "订单处理", "支付记录", "库存统计"));
            roles.addAll(List.of("商家", "会员用户"));
        } else if (containsAny(text, "预约", "挂号", "预订")) {
            domain = "预约管理";
            modules.addAll(List.of("资源排班", "预约申请", "审核确认", "签到核销", "预约统计"));
            roles.addAll(List.of("预约用户", "审核员"));
        } else if (containsAny(text, "社团", "活动", "报名")) {
            domain = "社团管理";
            modules.addAll(List.of("社团档案", "成员管理", "活动发布", "报名审核", "经费记录"));
            roles.addAll(List.of("社团负责人", "学生会员"));
        } else if (containsAny(text, "公开数据", "数据处理", "数据集", "数据分析")) {
            domain = "公开数据分析";
            modules.addAll(List.of("数据导入", "数据清洗", "指标计算", "可视化看板", "报表导出"));
        } else if (containsAny(text, "数据", "可视化", "分析")) {
            domain = "数据分析可视化";
            modules.addAll(List.of("数据导入", "指标看板", "趋势分析", "图表配置", "报表导出"));
        } else {
            modules.addAll(List.of("业务档案", "流程审批", "数据统计", "文件管理", "系统配置"));
        }
        String base = domainSlug(domain);
        List<TablePlan> tables = List.of(
                new TablePlan("sys_user", "系统用户", List.of("id", "username", "password_hash", "role", "phone", "status", "created_at")),
                new TablePlan(base + "_record", domain + "核心记录", List.of("id", "name", "code", "owner_id", "status", "remark", "created_at")),
                new TablePlan(base + "_audit", domain + "流程记录", List.of("id", "record_id", "action", "operator_id", "result", "created_at")),
                new TablePlan(base + "_notice", domain + "消息通知", List.of("id", "title", "content", "receiver_id", "read_flag", "created_at")),
                new TablePlan("system_setup", "系统初始化配置", List.of("id", "setup_key", "setup_value", "status", "created_at"))
        );
        List<String> apis = modules.stream().map(module -> "/api/" + safeSlug(module) + " - CRUD、分页查询、统计接口").toList();
        List<String> pages = List.of("登录页", "首页仪表盘", "用户管理", "核心业务页", "数据统计页", "消息中心", "个人中心");
        List<String> paperOutline = defaultPaperOutline();
        return new ComputerProjectPlan(job.getTitle(), domain, modules, roles.stream().distinct().toList(), tables, pages, apis, paperOutline);
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
            job.setErrorMessage("万量矩阵规划失败，已回退规则生成：" + compact(exception.getMessage()));
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
        List<FilePlan> queue = buildFileQueue(plan, techStack);
        return new ComputerPlanVO(plan.title(), projectType, techStack, plan.roles(), plan.modules(),
                plan.tables().stream().map(table -> new TablePlanVO(table.name(), table.comment(), table.fields())).toList(),
                plan.pages(), plan.apis(), plan.paperOutline(), projectDirectoryTree(queue), queue.stream().map(FilePlan::path).toList(),
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
        job.setTechStack(defaultText(config.techStack(), job.getTechStack()));
        job.setPointsCost(estimateCost(job.getProjectType(), config.generatePaper(), config.enablePreview()));
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.updateById(job);
    }

    private ComputerProjectPlan analyzeForBackground(ComputerGenerationJob job) {
        ComputerProjectPlan matrixPlan = generatePlanWithMatrix(job);
        if (matrixPlan != null) return matrixPlan;
        String type = recommendProjectType(new ComputerProjectPlan(job.getTitle(), defaultText(job.getProjectType(), "通用业务管理"), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()), job.getInputText());
        return planFromConfig(job, new ComputerGenerationConfig(job.getTitle(), type, job.getTechStack(),
                List.of("管理员", "普通用户"), List.of("用户认证", "首页仪表盘", "核心业务管理", "数据统计"),
                List.of(new TablePlanVO("sys_user", "系统用户", List.of("id", "username", "password_hash", "role", "status", "created_at")),
                        new TablePlanVO("business_record", "业务记录", List.of("id", "name", "code", "status", "remark", "created_at"))),
                List.of("登录页", "首页仪表盘", "核心业务页", "数据统计页", "用户管理页"),
                List.of("/api/users - 用户管理接口", "/api/business-records - 业务记录接口"),
                defaultPaperOutline(), true, true, true));
    }

    private List<FilePlan> buildFileQueue(ComputerProjectPlan plan, String techStack) {
        List<FilePlan> queue = new ArrayList<>();
        queue.add(new FilePlan("sql/schema.sql", "SQL生成", "数据库建表脚本"));
        queue.add(new FilePlan("sql/data.sql", "SQL生成", "初始化演示数据"));
        queue.add(new FilePlan("sql/init.sql", "SQL生成", "数据库初始化入口脚本"));
        List<TablePlan> tables = ensureSystemSetupTable(plan.tables());
        for (TablePlan table : tables) queue.add(new FilePlan("backend/src/main/java/com/dropai/generated/entity/" + className(table.name()) + ".java", "后端生成", table.comment() + "实体类"));
        for (TablePlan table : tables) queue.add(new FilePlan("backend/src/main/java/com/dropai/generated/mapper/" + className(table.name()) + "Mapper.java", "后端生成", table.comment() + "Mapper"));
        for (TablePlan table : tables) queue.add(new FilePlan("backend/src/main/java/com/dropai/generated/service/" + className(table.name()) + "Service.java", "后端生成", table.comment() + "业务接口"));
        for (TablePlan table : tables) queue.add(new FilePlan("backend/src/main/java/com/dropai/generated/service/impl/" + className(table.name()) + "ServiceImpl.java", "后端生成", table.comment() + "业务实现"));
        for (TablePlan table : tables) queue.add(new FilePlan("backend/src/main/java/com/dropai/generated/controller/" + className(table.name()) + "Controller.java", "后端生成", table.comment() + "REST接口"));
        queue.add(new FilePlan("backend/src/main/java/com/dropai/generated/config/WebConfig.java", "后端生成", "跨域与Web配置"));
        queue.add(new FilePlan("backend/pom.xml", "后端生成", "Maven工程配置"));
        queue.add(new FilePlan("backend/src/main/java/com/dropai/generated/GeneratedApplication.java", "后端生成", "SpringBoot启动类"));
        queue.add(new FilePlan("frontend/package.json", "前端生成", "前端依赖配置"));
        queue.add(new FilePlan("frontend/vite.config.js", "前端生成", "Vite配置"));
        queue.add(new FilePlan("frontend/index.html", "前端生成", "前端入口HTML"));
        queue.add(new FilePlan("frontend/src/api/request.js", "前端生成", "Axios请求封装"));
        queue.add(new FilePlan("frontend/src/router/index.js", "前端生成", "Vue Router路由"));
        queue.add(new FilePlan("frontend/src/components/DataCard.vue", "前端生成", "通用数据卡片组件"));
        queue.add(new FilePlan("frontend/src/views/Login.vue", "前端生成", "登录页"));
        queue.add(new FilePlan("frontend/src/views/Dashboard.vue", "前端生成", "首页仪表盘"));
        queue.add(new FilePlan("frontend/src/views/Business.vue", "前端生成", "核心业务管理页"));
        queue.add(new FilePlan("frontend/src/views/Statistics.vue", "前端生成", "数据统计页"));
        queue.add(new FilePlan("frontend/src/views/UserManage.vue", "前端生成", "用户管理页"));
        queue.add(new FilePlan("frontend/src/App.vue", "前端生成", "根组件"));
        queue.add(new FilePlan("frontend/src/main.js", "前端生成", "前端启动入口"));
        queue.add(new FilePlan("README.md", "论文生成", "项目运行说明"));
        queue.add(new FilePlan("paper/论文大纲.md", "论文生成", "论文大纲"));
        queue.add(new FilePlan("paper/毕业论文.docx", "论文生成", "可打开的论文文档内容"));
        return queue;
    }

    private String generateValidatedFile(ComputerProjectPlan plan, String techStack, FilePlan file, List<FileSummary> summaries) {
        String fallback = fallbackFileContent(plan, techStack, file);
        for (int attempt = 0; attempt < 3; attempt++) {
            String content = attempt == 0 ? generateFileWithMatrix(plan, techStack, file, summaries) : fallback;
            if (content == null || content.isBlank()) content = fallback;
            if (validateFile(file, content, plan)) return content;
        }
        throw new IllegalStateException(file.path() + " 校验失败");
    }

    private String generateFileWithMatrix(ComputerProjectPlan plan, String techStack, FilePlan file, List<FileSummary> summaries) {
        if (!matrixDesignService.apiKeyConfigured()) return "";
        String instructions = """
                你是全栈工程师。只生成当前文件的完整内容，不要解释，不要 Markdown 代码围栏。
                生成内容必须是毕业设计/课程设计/管理系统/数据分析系统的合法合规代码。
                不得生成安全测试、漏洞利用、扫描、爆破、绕过认证、代理池、未授权数据采集等内容。
                代码必须可运行，并与给定数据库表、接口约定和命名规范一致。
                """;
        String input = """
                项目题目：%s
                技术栈：%s
                用户角色：%s
                功能模块：%s
                数据库表：%s
                后端接口：%s
                当前文件路径：%s
                当前文件职责：%s
                已生成文件摘要：%s
                """.formatted(plan.title(), techStack, plan.roles(), plan.modules(), plan.tables(), plan.apis(),
                file.path(), file.responsibility(), summaries);
        try {
            return matrixDesignService.generate(instructions, input).replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```$", "").trim();
        } catch (Exception exception) {
            return "";
        }
    }

    private String fallbackFileContent(ComputerProjectPlan plan, String techStack, FilePlan file) {
        String path = file.path();
        if ("sql/schema.sql".equals(path)) return schemaSql(plan);
        if ("sql/data.sql".equals(path)) return "INSERT INTO system_setup(setup_key,setup_value,status) VALUES ('admin_password_policy','FIRST_RUN_SET_PASSWORD','PENDING');\n";
        if ("sql/init.sql".equals(path)) return "SOURCE schema.sql;\nSOURCE data.sql;\n";
        if (path.endsWith("pom.xml")) return backendPom();
        if (path.endsWith("GeneratedApplication.java")) return "package com.dropai.generated;\n\nimport org.springframework.boot.SpringApplication;\nimport org.springframework.boot.autoconfigure.SpringBootApplication;\n\n@SpringBootApplication\npublic class GeneratedApplication {\n    public static void main(String[] args) { SpringApplication.run(GeneratedApplication.class, args); }\n}\n";
        if (path.endsWith("WebConfig.java")) return "package com.dropai.generated.config;\n\nimport org.springframework.context.annotation.Configuration;\nimport org.springframework.web.servlet.config.annotation.CorsRegistry;\nimport org.springframework.web.servlet.config.annotation.WebMvcConfigurer;\n\n@Configuration\npublic class WebConfig implements WebMvcConfigurer {\n    @Override public void addCorsMappings(CorsRegistry registry) { registry.addMapping(\"/api/**\").allowedOrigins(\"*\").allowedMethods(\"GET\",\"POST\",\"PUT\",\"DELETE\"); }\n}\n";
        if (path.contains("/entity/")) return javaEntity(path, plan);
        if (path.contains("/mapper/")) return javaMapper(path);
        if (path.contains("/service/impl/")) return javaServiceImpl(path);
        if (path.contains("/service/")) return javaService(path);
        if (path.contains("/controller/")) return javaController(path);
        if ("frontend/package.json".equals(path)) return "{\"scripts\":{\"dev\":\"vite --host 0.0.0.0\",\"build\":\"vite build\"},\"dependencies\":{\"@vitejs/plugin-vue\":\"latest\",\"vite\":\"latest\",\"vue\":\"latest\",\"vue-router\":\"latest\",\"axios\":\"latest\",\"element-plus\":\"latest\"},\"devDependencies\":{}}\n";
        if ("frontend/vite.config.js".equals(path)) return "import { defineConfig } from 'vite'\nimport vue from '@vitejs/plugin-vue'\nexport default defineConfig({ plugins: [vue()], server: { port: 5173 } })\n";
        if ("frontend/index.html".equals(path)) return "<div id=\"app\"></div><script type=\"module\" src=\"/src/main.js\"></script>\n";
        if ("frontend/src/api/request.js".equals(path)) return "import axios from 'axios'\nexport const request = axios.create({ baseURL: '/api', timeout: 15000 })\nexport const listRecords = (name) => request.get(`/${name}`)\n";
        if ("frontend/src/router/index.js".equals(path)) return "import { createRouter, createWebHistory } from 'vue-router'\nimport Login from '../views/Login.vue'\nimport Dashboard from '../views/Dashboard.vue'\nimport Business from '../views/Business.vue'\nimport Statistics from '../views/Statistics.vue'\nimport UserManage from '../views/UserManage.vue'\nexport default createRouter({ history: createWebHistory(), routes: [{path:'/',redirect:'/dashboard'},{path:'/login',component:Login},{path:'/dashboard',component:Dashboard},{path:'/business',component:Business},{path:'/statistics',component:Statistics},{path:'/users',component:UserManage}] })\n";
        if (path.endsWith("DataCard.vue")) return vue("DataCard", "<article class=\"card\"><strong>{{ title }}</strong><span>{{ value }}</span></article>", "const props = defineProps({ title: String, value: [String, Number] })");
        if (path.endsWith("Login.vue")) return vue("Login", "<main class=\"page\"><h1>" + plan.title() + "</h1><p>首次运行时创建管理员账户并设置强密码。</p><input placeholder=\"手机号\"/><input placeholder=\"密码\" type=\"password\"/><button>登录</button></main>", "");
        if (path.endsWith("Dashboard.vue")) return vue("Dashboard", "<main class=\"page\"><h1>首页仪表盘</h1><section class=\"grid\"><DataCard title=\"用户\" value=\"128\"/><DataCard title=\"待办\" value=\"16\"/><DataCard title=\"通知\" value=\"9\"/></section></main>", "import DataCard from '../components/DataCard.vue'");
        if (path.endsWith("Business.vue")) return vue("Business", "<main class=\"page\"><h1>核心业务管理</h1><ul><li v-for=\"item in modules\" :key=\"item\">{{ item }}</li></ul></main>", "const modules = " + toJsArray(plan.modules()));
        if (path.endsWith("Statistics.vue")) return vue("Statistics", "<main class=\"page\"><h1>数据统计</h1><p>按月份、状态和业务类型展示趋势分析。</p></main>", "");
        if (path.endsWith("UserManage.vue")) return vue("UserManage", "<main class=\"page\"><h1>用户管理</h1><p>角色：" + String.join("、", plan.roles()) + "</p></main>", "");
        if (path.endsWith("App.vue")) return "<template><router-view /></template>\n<script setup></script>\n<style>body{margin:0;font-family:Arial,'Microsoft YaHei',sans-serif;background:#f5f7fb}.page{padding:32px}.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}.card{display:block;background:white;border:1px solid #dbe5f4;border-radius:8px;padding:18px}button{background:#2563eb;color:white;border:0;border-radius:6px;padding:10px 18px}</style>\n";
        if (path.endsWith("main.js")) return "import { createApp } from 'vue'\nimport ElementPlus from 'element-plus'\nimport 'element-plus/dist/index.css'\nimport App from './App.vue'\nimport router from './router'\ncreateApp(App).use(router).use(ElementPlus).mount('#app')\n";
        if ("README.md".equals(path)) return "# " + plan.title() + "\n\n## 项目简介\n" + plan.domain() + "毕业设计项目。\n\n## 运行说明\n1. 导入 sql/schema.sql 和 sql/data.sql。\n2. 启动 backend SpringBoot 服务。\n3. 进入 frontend 执行 npm install && npm run dev。\n\n## 安全说明\n系统首次启动时创建管理员账户并设置强密码，不提供默认弱密码。\n";
        if (path.endsWith("论文大纲.md")) return "# 论文大纲\n\n" + String.join("\n", plan.paperOutline().stream().map(item -> "- " + item).toList()) + "\n";
        if (path.endsWith("毕业论文.docx")) return "本文件为可由 Word 打开的毕业论文内容草稿。\n\n题目：" + plan.title() + "\n\n" + String.join("\n\n", plan.paperOutline()) + "\n";
        return file.responsibility() + "\n";
    }

    private boolean validateFile(FilePlan file, String content, ComputerProjectPlan plan) {
        if (content == null || content.trim().length() < 12) return false;
        String path = file.path();
        if (path.endsWith(".sql") && path.endsWith("schema.sql")) return content.toLowerCase(Locale.ROOT).contains("create table");
        if (path.endsWith(".vue")) return content.contains("<template") && content.contains("<script");
        if (path.contains("/controller/")) return content.contains("@RestController") && content.contains("@RequestMapping");
        if (path.contains("/service/")) return content.contains("list") || content.contains("save") || content.contains("Service");
        if (path.contains("/entity/")) return content.contains("class ") && plan.tables().stream().anyMatch(table -> content.contains(className(table.name())));
        if (path.endsWith(".java")) return content.contains("package com.dropai.generated");
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
        if (text.contains("python") || text.contains("数据分析") || text.contains("公开数据")) return "Python Web 项目";
        if (text.contains("小程序") || text.contains("微信")) return "微信小程序后台项目";
        return "Java Web 项目";
    }

    private static String recommendTechStack(String projectType, String input) {
        String text = defaultText(input, "").toLowerCase(Locale.ROOT);
        if (projectType.contains("Python") && text.contains("django")) return "Django + MySQL";
        if (projectType.contains("Python") && text.contains("fastapi")) return "FastAPI + Vue + MySQL";
        if (projectType.contains("Python")) return "Flask + Vue + MySQL";
        if (text.contains("thymeleaf")) return "Spring Boot + Thymeleaf + MySQL";
        return "Spring Boot + Vue + MySQL";
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
        return """
                project/
                ├── frontend/
                ├── backend/
                ├── sql/
                ├── paper/
                ├── README.md
                └── preview/

                文件队列：
                """ + String.join("\n", queue.stream().map(file -> "- " + file.path()).toList());
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
                                 List<String> apis, List<String> paperOutline, String directoryTree,
                                 List<String> fileQueue, int pointsCost,
                                 boolean generatePaper, boolean generateTests, boolean enablePreview) {}
    public record ComputerGenerationConfig(String title, String projectType, String techStack, List<String> roles,
                                           List<String> modules, List<TablePlanVO> tables, List<String> pages,
                                           List<String> apis, List<String> paperOutline,
                                           boolean generatePaper, boolean generateTests, boolean enablePreview) {}
    public record TablePlanVO(String name, String comment, List<String> fields) {}
    public record ComputerJobVO(String id, String title, String projectType, String techStack, String status,
                                int progress, String currentStage, String currentFile, String errorMessage, int pointsCost,
                                String previewUrl, String downloadUrl, List<GeneratedFileVO> files,
                                String activePreviewUrl, List<String> stages) {}
    public record GeneratedFileVO(String fileType, String fileName, long fileSize, String downloadUrl) {}
    private record ComputerProjectPlan(String title, String domain, List<String> modules, List<String> roles,
                                       List<TablePlan> tables, List<String> pages, List<String> apis,
                                       List<String> paperOutline) {}
    private record TablePlan(String name, String comment, List<String> fields) {}
    private record FilePlan(String path, String stage, String responsibility) {}
    private record FileSummary(String path, String summary) {}
}
