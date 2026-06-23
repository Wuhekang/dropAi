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
    private static final List<String> STAGES = List.of(
            "正在解析任务书", "正在识别项目类型", "正在生成数据库设计", "正在生成后端接口",
            "正在生成前端页面", "正在生成论文", "正在打包成果", "正在启动网页预览", "生成完成"
    );
    private static final Path ROOT = Paths.get("work", "computer-generator").toAbsolutePath().normalize();

    private final ComputerGenerationJobMapper jobMapper;
    private final ComputerGeneratedFileMapper fileMapper;
    private final ComputerPreviewInstanceMapper previewMapper;
    private final DocumentParser documentParser;
    private final PointService pointService;
    private final MatrixDesignService matrixDesignService;
    private final ObjectMapper objectMapper;

    public ComputerGeneratorService(ComputerGenerationJobMapper jobMapper, ComputerGeneratedFileMapper fileMapper,
                                    ComputerPreviewInstanceMapper previewMapper, DocumentParser documentParser,
                                    PointService pointService, MatrixDesignService matrixDesignService,
                                    ObjectMapper objectMapper) {
        this.jobMapper = jobMapper;
        this.fileMapper = fileMapper;
        this.previewMapper = previewMapper;
        this.documentParser = documentParser;
        this.pointService = pointService;
        this.matrixDesignService = matrixDesignService;
        this.objectMapper = objectMapper;
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
        try {
            if (config != null) {
                applyConfig(job, config);
            }
            if (!Boolean.TRUE.equals(job.getPointsCharged())) {
                pointService.deductCustom(job.getUserId(), job.getId(), "COMPUTER_GENERATE", "计算机程序包生成",
                        value(job.getPointsCost()), "生成 " + job.getTitle());
                job.setPointsCharged(true);
                jobMapper.updateById(job);
            }
            ComputerProjectPlan plan = config == null ? analyze(job) : planFromConfig(job, config);
            Path jobRoot = ROOT.resolve(job.getUserId().toString()).resolve(job.getId()).normalize();
            assertUnderRoot(jobRoot);
            recreateDirectory(jobRoot);

            updateStage(job, 1, "RUNNING", "");
            writeReadme(jobRoot, plan, job);
            updateStage(job, 2, "RUNNING", "");
            Path sql = writeSql(jobRoot, plan);
            updateStage(job, 3, "RUNNING", "");
            Path backend = writeBackend(jobRoot, plan, job.getTechStack());
            updateStage(job, 4, "RUNNING", "");
            Path frontend = writeFrontend(jobRoot, plan);
            updateStage(job, 5, "RUNNING", "");
            Path paper = writePaper(jobRoot, plan, job.getTechStack());
            ComputerPreviewInstance preview = writePreview(jobRoot, plan, job);
            updateStage(job, 6, "RUNNING", "");
            Path zip = jobRoot.resolve("computer-project-package.zip");
            zip(jobRoot, zip);
            updateStage(job, 7, "RUNNING", "");

            job.setOutputZipPath(zip.toString());
            job.setFrontendPath(frontend.toString());
            job.setBackendPath(backend.toString());
            job.setSqlPath(sql.toString());
            job.setPaperPath(paper.toString());
            job.setPreviewUrl(preview.getPreviewUrl());
            updateStage(job, 8, "SUCCESS", "");
            registerFiles(job.getId(), jobRoot, zip);
            return result(jobId);
        } catch (Exception exception) {
            job.setStatus("FAILED");
            job.setErrorMessage(stageMessage(job) + "失败：" + compact(exception.getMessage()));
            job.setUpdatedAt(LocalDateTime.now());
            jobMapper.updateById(job);
            throw new IllegalStateException(job.getErrorMessage(), exception);
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
        } else if (containsAny(text, "爬虫", "采集")) {
            domain = "Python 爬虫采集";
            modules.addAll(List.of("采集任务", "代理配置", "数据清洗", "结果入库", "采集监控"));
        } else if (containsAny(text, "数据", "可视化", "分析")) {
            domain = "数据分析可视化";
            modules.addAll(List.of("数据导入", "指标看板", "趋势分析", "图表配置", "报表导出"));
        } else {
            modules.addAll(List.of("业务档案", "流程审批", "数据统计", "文件管理", "系统配置"));
        }
        String base = domainSlug(domain);
        List<TablePlan> tables = List.of(
                new TablePlan("sys_user", "系统用户", List.of("id", "username", "password", "role", "phone", "status", "created_at")),
                new TablePlan(base + "_record", domain + "核心记录", List.of("id", "name", "code", "owner_id", "status", "remark", "created_at")),
                new TablePlan(base + "_audit", domain + "流程记录", List.of("id", "record_id", "action", "operator_id", "result", "created_at")),
                new TablePlan(base + "_notice", domain + "消息通知", List.of("id", "title", "content", "receiver_id", "read_flag", "created_at"))
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
                你是 DropAI 的计算机毕业设计程序包生成规划器。请根据用户题目、任务书/开题报告文本和技术栈，生成可落盘的程序包规划。
                必须主动补全合理需求，不能返回信息不足。不要把所有项目都写成学生管理系统。
                只返回 JSON，不要 Markdown，不要解释。
                JSON 结构：
                {
                  "domain": "业务场景名称",
                  "modules": ["模块1", "模块2"],
                  "roles": ["管理员", "普通用户"],
                  "tables": [{"name":"英文小写表名","comment":"中文表说明","fields":["id","name","status","created_at"]}],
                  "pages": ["登录页", "首页仪表盘"],
                  "apis": ["/api/example - 接口说明"],
                  "paperOutline": ["摘要", "Abstract"],
                  "implementationNotes": ["生成重点"]
                }
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
        StringBuilder sql = new StringBuilder("CREATE DATABASE IF NOT EXISTS dropai_generated DEFAULT CHARSET utf8mb4;\nUSE dropai_generated;\n\n");
        for (TablePlan table : plan.tables()) {
            sql.append("CREATE TABLE IF NOT EXISTS ").append(table.name()).append(" (\n");
            for (String field : table.fields()) {
                if ("id".equals(field)) sql.append("  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',\n");
                else if (field.endsWith("_id")) sql.append("  ").append(field).append(" BIGINT COMMENT '关联ID',\n");
                else if (field.endsWith("_at")) sql.append("  ").append(field).append(" DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '时间字段',\n");
                else sql.append("  ").append(field).append(" VARCHAR(255) COMMENT '").append(field).append("',\n");
            }
            sql.append("  INDEX idx_").append(table.name()).append("_status (status)\n) COMMENT='").append(table.comment()).append("';\n\n");
        }
        sql.append("INSERT INTO sys_user(username,password,role,phone,status) VALUES ('admin','admin123','ADMIN','13800000000','ENABLED');\n");
        sql.append("INSERT INTO ").append(plan.tables().get(1).name()).append("(name,code,status,remark) VALUES ('示例").append(plan.domain()).append("记录','DEMO001','ACTIVE','系统生成示例数据');\n");
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
        write(root.resolve("README.md"), "# " + plan.title() + "\n\n业务场景：" + plan.domain() + "\n\n目录包含 frontend、backend、sql、paper、preview。\n\n运行说明：导入 sql/schema.sql，启动后端，再运行前端 Vite 项目。\n");
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
                value(job.getProgress()), stageMessage(job), job.getErrorMessage(), value(job.getPointsCost()),
                job.getPreviewUrl(), "/api/computer-generator/download/" + job.getId(),
                files.stream().map(file -> new GeneratedFileVO(file.getFileType(), file.getFileName(), value(file.getFileSize()),
                        "/api/computer-generator/download-file/" + job.getId() + "?fileName=" + urlEncode(file.getFileName()))).toList(),
                preview == null ? null : preview.getPreviewUrl(), STAGES);
    }

    private ComputerPlanVO toPlanVO(ComputerProjectPlan plan, String projectType, String techStack, int cost) {
        return new ComputerPlanVO(plan.title(), projectType, techStack, plan.roles(), plan.modules(),
                plan.tables().stream().map(table -> new TablePlanVO(table.name(), table.comment(), table.fields())).toList(),
                plan.pages(), plan.apis(), plan.paperOutline(), cost, true, true, true);
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
        if (text.contains("采集") || text.contains("爬虫")) return "crawlers";
        if (text.contains("统计") || text.contains("分析")) return "analytics";
        return "business";
    }

    private static String domainSlug(String domain) {
        if (domain.contains("宿舍")) return "dormitory";
        if (domain.contains("图书")) return "library";
        if (domain.contains("商城")) return "mall";
        if (domain.contains("预约")) return "reservation";
        if (domain.contains("社团")) return "club";
        if (domain.contains("爬虫")) return "crawler";
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

    private static String extractTitle(String input, List<String> names) {
        String text = defaultText(input, "");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:题目|课题|项目名称)[:：\\s]*([^\\n。；;]{4,40})").matcher(text);
        if (matcher.find()) return matcher.group(1).trim();
        if (text.contains("宿舍")) return "学生宿舍管理系统";
        if (text.contains("图书")) return "图书管理系统";
        if (text.contains("商城")) return "在线商城系统";
        if (text.contains("预约")) return "预约管理系统";
        if (text.contains("社团")) return "社团管理系统";
        if (text.contains("爬虫")) return "Python数据采集系统";
        if (!names.isEmpty()) return names.get(0).replaceAll("\\.[^.]+$", "").replace("任务书", "").replace("开题报告", "") + "系统";
        return "计算机毕业设计管理系统";
    }

    private static String recommendProjectType(ComputerProjectPlan plan, String input) {
        String text = (plan.domain() + input).toLowerCase(Locale.ROOT);
        if (text.contains("python") || text.contains("爬虫") || text.contains("数据分析")) return "Python Web 项目";
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
                                 List<String> apis, List<String> paperOutline, int pointsCost,
                                 boolean generatePaper, boolean generateTests, boolean enablePreview) {}
    public record ComputerGenerationConfig(String title, String projectType, String techStack, List<String> roles,
                                           List<String> modules, List<TablePlanVO> tables, List<String> pages,
                                           List<String> apis, List<String> paperOutline,
                                           boolean generatePaper, boolean generateTests, boolean enablePreview) {}
    public record TablePlanVO(String name, String comment, List<String> fields) {}
    public record ComputerJobVO(String id, String title, String projectType, String techStack, String status,
                                int progress, String currentStage, String errorMessage, int pointsCost,
                                String previewUrl, String downloadUrl, List<GeneratedFileVO> files,
                                String activePreviewUrl, List<String> stages) {}
    public record GeneratedFileVO(String fileType, String fileName, long fileSize, String downloadUrl) {}
    private record ComputerProjectPlan(String title, String domain, List<String> modules, List<String> roles,
                                       List<TablePlan> tables, List<String> pages, List<String> apis,
                                       List<String> paperOutline) {}
    private record TablePlan(String name, String comment, List<String> fields) {}
}
