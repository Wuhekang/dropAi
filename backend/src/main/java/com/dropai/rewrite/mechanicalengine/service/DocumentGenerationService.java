package com.dropai.rewrite.mechanicalengine.service;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.mechanicalengine.domain.DocumentGenerationResult;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DocumentGenerationService {
    private final MechanicalJobService mechanicalJobs;
    private final MechanicalDocumentAgent documentAgent;
    private final DocumentJobMapper mapper;
    private final TaskExecutor executor;
    private final ConcurrentHashMap<String, DocumentGenerationResult> jobs = new ConcurrentHashMap<>();

    public DocumentGenerationService(MechanicalJobService mechanicalJobs, MechanicalDocumentAgent documentAgent,
                                     DocumentJobMapper mapper, TaskExecutor executor) {
        this.mechanicalJobs = mechanicalJobs;
        this.documentAgent = documentAgent;
        this.mapper = mapper;
        this.executor = executor;
    }

    public DocumentGenerationResult start(String resultId, String documentType) {
        mechanicalJobs.requireResult(resultId);
        Long userId = AuthContext.requireUserId();
        String jobId = "mechanical_document_" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        DocumentGenerationResult created = new DocumentGenerationResult(jobId, resultId, normalize(documentType),
                "CREATED", 0, "Document job queued", List.of(), now, now);
        jobs.put(jobId, created);
        executor.execute(() -> run(jobId, resultId, normalize(documentType), userId));
        return created;
    }

    public DocumentGenerationResult get(String jobId) {
        DocumentGenerationResult result = jobs.get(jobId);
        if (result == null) throw new IllegalArgumentException("DOCUMENT_JOB_NOT_FOUND");
        return result;
    }

    private void run(String jobId, String resultId, String type, Long userId) {
        DocumentGenerationResult previous = jobs.get(jobId);
        jobs.put(jobId, new DocumentGenerationResult(jobId, resultId, type, "RUNNING", 20,
                "Generating documents from the immutable mechanical result", List.of(), previous.createdAt(), LocalDateTime.now()));
        try {
            MechanicalProject project = mechanicalJobs.requireProjectByResult(resultId);
            List<MechanicalProject.Artifact> artifacts = new ArrayList<>();
            Path pdf = Files.createTempFile(jobId, ".pdf");
            documentAgent.generate(project, pdf);
            artifacts.add(persist(userId, jobId + "_pdf", type + "_Design_Report.pdf", "application/pdf", Files.readAllBytes(pdf)));
            artifacts.add(persist(userId, jobId + "_docx", type + "_Design_Report.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx(project, type)));
            jobs.put(jobId, new DocumentGenerationResult(jobId, resultId, type, "COMPLETED", 100,
                    "Document result is ready", List.copyOf(artifacts), previous.createdAt(), LocalDateTime.now()));
        } catch (Exception exception) {
            jobs.put(jobId, new DocumentGenerationResult(jobId, resultId, type, "FAILED", 100,
                    exception.getMessage(), List.of(), previous.createdAt(), LocalDateTime.now()));
        }
    }

    private byte[] docx(MechanicalProject project, String type) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("DropAI Mechanical " + type + " Report");
            document.createParagraph().createRun().setText("Product: " + project.getProductName());
            document.createParagraph().createRun().setText("Concept: " + project.getConcept().getSelectedConcept());
            document.createParagraph().createRun().setText("Parts: " + project.getParts().size());
            for (MechanicalProject.CADModelSpec part : project.getParts()) {
                document.createParagraph().createRun().setText(part.partNumber() + " " + part.name() + " | " + part.material() + " | " + part.manufacturing());
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    private MechanicalProject.Artifact persist(Long userId, String recordId, String name, String mediaType, byte[] bytes) {
        DocumentJobRecord record = new DocumentJobRecord();
        record.setJobId(recordId); record.setUserId(userId); record.setFileName(name); record.setSourceFeature("MECHANICAL_DOCUMENT_ENGINE");
        record.setMode("mechanical_document"); record.setModeName("Mechanical Document"); record.setPlatform("DOCUMENT_AGENT"); record.setPlatformName("Mechanical Document Agent");
        record.setStatus("SUCCESS"); record.setTotalParagraphs(1); record.setProcessedParagraphs(1); record.setRewrittenParagraphs(1);
        record.setMessage("Generated from existing mechanical result"); record.setParagraphsJson("[]"); record.setOutputFile(bytes);
        record.setCreatedAt(LocalDateTime.now()); record.setUpdatedAt(LocalDateTime.now()); mapper.insert(record);
        return new MechanicalProject.Artifact(name, "DOCUMENT", mediaType, bytes.length, "/api/documents/" + recordId + "/download", true);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "ENGINEERING" : value.trim().toUpperCase();
    }
}
