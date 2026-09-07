package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.config.DoubaoProperties;
import com.dropai.rewrite.service.WorkflowRewriteService;
import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DocumentRewriteFinalGuardTest {

    @Test
    void validUnchangedFinalParagraphIsSuccessfulAfterOneWorkflowCall() throws Exception {
        String original = "该平台负责核对资料，并记录现场处理情况。";
        WorkflowRewriteService workflow = workflowReturning(original);

        try (XWPFDocument document = documentWithBody(original)) {
            GuardResult result = runMode(workflow, document, original.length(), "humanize");

            assertThat(result.success()).isTrue();
            assertThat(result.rewrittenText()).isEqualTo(original);
            assertThat(result.errorMessage()).isEmpty();
            verify(workflow).execute(original, "humanize");
            verifyNoMoreInteractions(workflow);
        }
    }

    @Test
    void lengthCompressionMayReturnToOriginalAfterSuccessfulModelProcessing() throws Exception {
        String original = "该平台负责核对资料，并记录现场处理情况，同时保留必要的核验记录供后续复查。";
        String expanded = original + "这项补充说明具有重要意义，可以为后续工作提供参考，并进一步体现整体优化效果。";
        WorkflowRewriteService workflow = workflowReturning(expanded);

        try (XWPFDocument document = documentWithBody(original)) {
            GuardResult result = runMode(workflow, document, original.length(), "humanize");

            assertThat(result.success()).isTrue();
            assertThat(result.rewrittenText()).isEqualTo(original);
            verify(workflow).execute(original, "humanize");
        }
    }

    @Test
    void doubleModeCanTravelFromOriginalToDraftAndBackAfterBothStagesProcessIt() throws Exception {
        String original = "该平台负责核对资料，并记录现场处理情况。";
        WorkflowRewriteService workflow = mock(WorkflowRewriteService.class);
        WorkflowRewriteService.WorkflowRewriteResult rewriteDraft = workflowResult("资料由平台核对，现场情况同步记录。");
        WorkflowRewriteService.WorkflowRewriteResult humanizeDraft = workflowResult(original);
        when(workflow.execute(anyString(), anyString())).thenReturn(rewriteDraft, humanizeDraft);

        try (XWPFDocument document = documentWithBody(original)) {
            GuardResult result = runMode(workflow, document, original.length(), "double");

            assertThat(result.success()).isTrue();
            assertThat(result.rewrittenText()).isEqualTo(original);
            assertThat(result.errorMessage()).isEmpty();
            verify(workflow).execute(original, "rewrite");
            verify(workflow).execute(rewriteDraft.getRewrittenText(), "humanize");
            verifyNoMoreInteractions(workflow);
        }
    }

    @Test
    void doubleModeStillRunsSecondStageWhenFirstStageReturnsOriginal() throws Exception {
        String original = "该平台负责核对资料，并记录现场处理情况。";
        WorkflowRewriteService workflow = workflowReturning(original);
        try (XWPFDocument document = documentWithBody(original)) {
            assertThat(runMode(workflow, document, original.length(), "double").success()).isTrue();
            verify(workflow).execute(original, "rewrite");
            verify(workflow).execute(original, "humanize");
            verifyNoMoreInteractions(workflow);
        }
    }

    @Test
    void emptyWorkflowResponseCannotBeCountedAsProcessedSuccessfully() throws Exception {
        String original = "该平台负责核对资料，并记录现场处理情况。";
        WorkflowRewriteService workflow = workflowReturning("  ");
        try (XWPFDocument document = documentWithBody(original)) {
            GuardResult result = runMode(workflow, document, original.length(), "humanize");
            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("未返回有效段落内容");
            assertThat(result.rewrittenText()).isEqualTo(original);
        }
    }

    @Test
    void failedRewriteStageCannotBecomeSameTextSuccessOrProceedToHumanize() throws Exception {
        String original = "该平台负责核对资料，并记录现场处理情况。";
        WorkflowRewriteService workflow = mock(WorkflowRewriteService.class);
        when(workflow.execute(anyString(), eq("rewrite"))).thenThrow(new IllegalStateException("模型调用超时"));
        try (XWPFDocument document = documentWithBody(original)) {
            GuardResult result = runMode(workflow, document, original.length(), "double");
            assertThat(result.success()).isFalse();
            assertThat(result.rewrittenText()).isEqualTo(original);
            assertThat(result.errorMessage()).contains("模型调用超时");
            verify(workflow).execute(original, "rewrite");
            verifyNoMoreInteractions(workflow);
        }
    }

    @Test
    void pureRewriteKeepsItsExistingDocumentFlow() throws Exception {
        String original = "该平台负责核对资料，并记录现场处理情况。";
        WorkflowRewriteService workflow = workflowReturning(original);

        try (XWPFDocument document = documentWithBody(original)) {
            GuardResult result = runMode(workflow, document, original.length(), "rewrite");

            assertThat(result.success()).isTrue();
            assertThat(result.rewrittenText()).isEqualTo(original);
        }
    }

    private WorkflowRewriteService workflowReturning(String rewrittenText) {
        WorkflowRewriteService workflow = mock(WorkflowRewriteService.class);
        when(workflow.execute(anyString(), anyString())).thenReturn(workflowResult(rewrittenText));
        return workflow;
    }

    private WorkflowRewriteService.WorkflowRewriteResult workflowResult(String rewrittenText) {
        WorkflowRewriteService.WorkflowRewriteResult result = new WorkflowRewriteService.WorkflowRewriteResult();
        result.setRewrittenText(rewrittenText);
        return result;
    }

    private XWPFDocument documentWithBody(String body) {
        XWPFDocument document = new XWPFDocument();
        document.createParagraph().createRun().setText("摘要");
        document.createParagraph().createRun().setText(body);
        document.createParagraph().createRun().setText("参考文献");
        return document;
    }

    private GuardResult runMode(
            WorkflowRewriteService workflow,
            XWPFDocument document,
            int originalLength,
            String mode
    ) throws Exception {
        DoubaoProperties properties = new DoubaoProperties();
        properties.setDocumentConcurrency(1);
        DocumentRewriteServiceImpl service = new DocumentRewriteServiceImpl(
                workflow, null, properties, null, null, null, null
        );
        try {
            DocumentRewriteJobVO job = new DocumentRewriteJobVO();
            job.setMode(mode);
            job.setModeName("double".equals(mode) ? "双降" : "智能降AI");
            job.setPlatform("GENERAL");

            Method collect = DocumentRewriteServiceImpl.class.getDeclaredMethod(
                    "collectRewriteTargets", DocumentRewriteJobVO.class, XWPFDocument.class
            );
            collect.setAccessible(true);
            List<?> targets = (List<?>) collect.invoke(service, job, document);

            Method rewrite = DocumentRewriteServiceImpl.class.getDeclaredMethod(
                    "rewriteTargetsWithLengthControl", DocumentRewriteJobVO.class, List.class, int.class
            );
            rewrite.setAccessible(true);
            List<?> results = (List<?>) rewrite.invoke(service, job, targets, originalLength);
            Object result = results.get(0);
            return new GuardResult(
                    (boolean) recordValue(result, "success"),
                    (String) recordValue(result, "rewrittenText"),
                    (String) recordValue(result, "errorMessage")
            );
        } finally {
            service.shutdown();
        }
    }

    private Object recordValue(Object record, String methodName) throws Exception {
        Method method = record.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(record);
    }

    private record GuardResult(boolean success, String rewrittenText, String errorMessage) {
    }
}
