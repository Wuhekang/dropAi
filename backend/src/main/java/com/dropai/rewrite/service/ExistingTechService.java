package com.dropai.rewrite.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExistingTechService {
    private final Map<String, ExistingTechTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, UploadedFileInfo> files = new ConcurrentHashMap<>();

    public UploadedFileInfo upload(MultipartFile file) {
        String fileId = "file-" + UUID.randomUUID();
        UploadedFileInfo info = new UploadedFileInfo(fileId, file.getOriginalFilename(), file.getSize(), "success", "文件已接收，等待处理");
        files.put(fileId, info);
        return info;
    }

    public ExistingTechTask submit(Map<String, Object> params) {
        String taskId = "task-" + UUID.randomUUID();
        ExistingTechTask task = new ExistingTechTask(taskId, "running", "处理中，请稍候", "", Instant.now().toString());
        tasks.put(taskId, task);
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1500);
                String result = mockResult(params);
                tasks.put(taskId, new ExistingTechTask(taskId, "success", "处理成功，结果已生成。", result, Instant.now().toString()));
            } catch (Exception exception) {
                tasks.put(taskId, new ExistingTechTask(taskId, "failed", "处理失败：" + exception.getMessage(), "", Instant.now().toString()));
            }
        });
        return task;
    }

    public ExistingTechTask status(String taskId) {
        return tasks.getOrDefault(taskId, new ExistingTechTask(taskId, "failed", "任务不存在", "", Instant.now().toString()));
    }

    public ExistingTechResult result(String taskId) {
        ExistingTechTask task = status(taskId);
        return new ExistingTechResult(task.taskId(), task.status(), task.result());
    }

    public byte[] download(String taskId) {
        return status(taskId).result().getBytes(StandardCharsets.UTF_8);
    }

    private String mockResult(Map<String, Object> params) {
        String featureName = text(params.get("featureName"), "现有技术处理");
        String style = text(params.get("outputStyle"), "学术");
        String strength = text(params.get("strength"), "标准");
        String source = text(params.get("text"), "");
        String fileName = text(params.get("fileName"), "");
        if (source.isBlank()) source = fileName.isBlank() ? "当前为模拟处理内容。" : "已接收上传文档：" + fileName;
        StringBuilder builder = new StringBuilder();
        builder.append("【").append(featureName).append("｜").append(style).append("风格｜").append(strength).append("处理】\n");
        String[] paragraphs = source.split("\\n+");
        int index = 1;
        for (String paragraph : paragraphs) {
            String value = paragraph.replaceAll("\\s+", " ").trim();
            if (value.isBlank()) continue;
            builder.append(index++).append(". ").append(value)
                    .append("。本段已按所选参数完成表达优化，保留核心语义、专业术语和段落逻辑。\n\n");
        }
        builder.append("注：当前为后端 mock 结果，真实 API 到位后替换 ExistingTechService 即可。");
        return builder.toString();
    }

    private String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    public record UploadedFileInfo(String fileId, String fileName, long size, String status, String message) {}
    public record ExistingTechTask(String taskId, String status, String message, String result, String updatedAt) {}
    public record ExistingTechResult(String taskId, String status, String result) {}
}
