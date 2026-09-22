package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.config.DoubaoProperties;
import com.dropai.rewrite.service.WorkflowRewriteService;
import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
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
    void doubleModeRunsOneRewriteThenImmediateHumanize() throws Exception {
        String original = "带式输送机主要由驱动装置、传动滚筒、改向滚筒、托辊、机架和张紧装置组成，各部分共同完成物料的连续输送。";
        WorkflowRewriteService workflow = mock(WorkflowRewriteService.class);
        WorkflowRewriteService.WorkflowRewriteResult rewriteDraft = workflowResult("动力装置带动滚筒运行，机架和托辊支撑输送带，其余构件负责改向和张紧。");
        WorkflowRewriteService.WorkflowRewriteResult humanizeDraft = workflowResult("电机带动滚筒运转，机架与托辊承托输送带，改向和张紧则由其余构件完成。");
        when(workflow.execute(anyString(), anyString()))
                .thenReturn(rewriteDraft, humanizeDraft);

        try (XWPFDocument document = documentWithBody(original)) {
            GuardResult result = runMode(workflow, document, original.length(), "double");

            assertThat(result.success()).isTrue();
            assertThat(result.rewrittenText()).isEqualTo(humanizeDraft.getRewrittenText());
            assertThat(result.errorMessage()).isEmpty();
            verify(workflow).execute(rewriteDraft.getRewrittenText(), "humanize");
            verify(workflow).execute(original, "rewrite");
            verifyNoMoreInteractions(workflow);
        }
    }

    @Test
    void doubleModeDoesNotStartExtraCandidateChainsWhenFinalTextMatchesOriginal() throws Exception {
        String original = "带式输送机主要由驱动装置、传动滚筒、改向滚筒、托辊、机架和张紧装置组成，各部分共同完成物料的连续输送。";
        String rewritten = "动力装置负责提供输送动力，滚筒、托辊、机架和张紧装置共同维持输送带运行。";
        WorkflowRewriteService workflow = mock(WorkflowRewriteService.class);
        when(workflow.execute(original, "rewrite")).thenReturn(workflowResult(rewritten));
        when(workflow.execute(rewritten, "humanize")).thenReturn(workflowResult(original));
        try (XWPFDocument document = documentWithBody(original)) {
            GuardResult result = runMode(workflow, document, original.length(), "double");
            assertThat(result.success()).isTrue();
            assertThat(result.rewrittenText()).isEqualTo(original);
            verify(workflow).execute(original, "rewrite");
            verify(workflow).execute(rewritten, "humanize");
            verifyNoMoreInteractions(workflow);
        }
    }

    @Test
    void doubleModeKeepsRewriteThenHumanizeOrderForEveryParagraph() throws Exception {
        String first = "现场人员先核对设备状态，再填写运行记录并提交负责人复查。";
        String second = "管理人员根据巡检记录确认故障位置，并安排对应人员完成维修。";
        String firstRewrite = "设备状态由现场人员核对，运行情况记录后交给负责人复查。";
        String firstHumanize = "现场人员核对设备状态，记下运行情况后交由负责人复查。";
        String secondRewrite = "管理人员查看巡检记录以确定故障位置，随后安排维修人员处理。";
        String secondHumanize = "管理人员从巡检记录中确认故障位置，再安排相应人员维修。";
        WorkflowRewriteService workflow = mock(WorkflowRewriteService.class);
        when(workflow.execute(first, "rewrite")).thenReturn(workflowResult(firstRewrite));
        when(workflow.execute(firstRewrite, "humanize")).thenReturn(workflowResult(firstHumanize));
        when(workflow.execute(second, "rewrite")).thenReturn(workflowResult(secondRewrite));
        when(workflow.execute(secondRewrite, "humanize")).thenReturn(workflowResult(secondHumanize));

        try (XWPFDocument document = documentWithBodies(first, second)) {
            List<GuardResult> results = runModeResults(
                    workflow, document, first.length() + second.length(), "double"
            );

            assertThat(results).extracting(GuardResult::rewrittenText)
                    .containsExactly(firstHumanize, secondHumanize);
            InOrder order = inOrder(workflow);
            order.verify(workflow).execute(first, "rewrite");
            order.verify(workflow).execute(firstRewrite, "humanize");
            order.verify(workflow).execute(second, "rewrite");
            order.verify(workflow).execute(secondRewrite, "humanize");
            order.verifyNoMoreInteractions();
        }
    }

    @Test
    void doubleModeAcceptsUnchangedDataDominantParagraphAfterBothStages() throws Exception {
        String original = "C30：97.51 m³；C20：15.80 m³。";
        WorkflowRewriteService workflow = workflowReturning(original);

        try (XWPFDocument document = documentWithBody(original)) {
            GuardResult result = runMode(workflow, document, original.length(), "double");

            assertThat(result.success()).isTrue();
            assertThat(result.rewrittenText()).isEqualTo(original);
            verify(workflow).execute(original, "rewrite");
            verify(workflow).execute(original, "humanize");
            verifyNoMoreInteractions(workflow);
        }
    }

    @Test
    void emptyWorkflowResponseFallsBackToCompleteOriginalParagraph() throws Exception {
        String original = "该平台负责核对资料，并记录现场处理情况。";
        WorkflowRewriteService workflow = workflowReturning("  ");
        try (XWPFDocument document = documentWithBody(original)) {
            GuardResult result = runMode(workflow, document, original.length(), "humanize");
            assertThat(result.success()).isTrue();
            assertThat(result.errorMessage()).isEmpty();
            assertThat(result.rewrittenText()).isEqualTo(original);
        }
    }

    @Test
    void failedRewriteStageFallsBackToOriginalWithoutProceedingToHumanize() throws Exception {
        String original = "该平台负责核对资料，并记录现场处理情况。";
        WorkflowRewriteService workflow = mock(WorkflowRewriteService.class);
        when(workflow.execute(anyString(), eq("rewrite"))).thenThrow(new IllegalStateException("模型调用超时"));
        try (XWPFDocument document = documentWithBody(original)) {
            GuardResult result = runMode(workflow, document, original.length(), "double");
            assertThat(result.success()).isTrue();
            assertThat(result.rewrittenText()).isEqualTo(original);
            assertThat(result.errorMessage()).isEmpty();
            verify(workflow).execute(original, "rewrite");
            verifyNoMoreInteractions(workflow);
        }
    }

    @Test
    void qualityGateFailureFallsBackToOriginalWithoutCustomerFacingError() throws Exception {
        String original = "（3）选择钢筋、混凝土和基础工程量进行复核。";
        WorkflowRewriteService workflow = mock(WorkflowRewriteService.class);
        when(workflow.execute(original, "rewrite"))
                .thenThrow(new RewriteQualityGateException("三个降重候选均未通过硬性门禁"));

        try (XWPFDocument document = documentWithBody(original)) {
            GuardResult result = runMode(workflow, document, original.length(), "rewrite");

            assertThat(result.success()).isTrue();
            assertThat(result.rewrittenText()).isEqualTo(original);
            assertThat(result.errorMessage()).isEmpty();
        }
    }

    @Test
    void doubleModeFallsBackWhenHumanizeChangesProtectedFacts() throws Exception {
        String original = "基础顶至11.350 m的柱配筋应按图纸设置。";
        String rewritten = "柱配筋应依据图纸，并从基础顶部开始设置至11.350 m。";
        String unsafeHumanized = "柱配筋应依据图纸，并从基础顶部开始设置至12.000 m。";
        WorkflowRewriteService workflow = mock(WorkflowRewriteService.class);
        when(workflow.execute(original, "rewrite")).thenReturn(workflowResult(rewritten));
        when(workflow.execute(rewritten, "humanize")).thenReturn(workflowResult(unsafeHumanized));

        try (XWPFDocument document = documentWithBody(original)) {
            GuardResult result = runMode(workflow, document, original.length(), "double");

            assertThat(result.success()).isTrue();
            assertThat(result.rewrittenText()).isEqualTo(original);
            assertThat(result.errorMessage()).isEmpty();
        }
    }

    @Test
    void pureRewriteAcceptsAnUnchangedLongParagraph() throws Exception {
        String original = "带式输送机主要由驱动装置、传动滚筒、改向滚筒、托辊、机架和张紧装置组成，各部分共同完成物料的连续输送。";
        WorkflowRewriteService workflow = workflowReturning(original);

        try (XWPFDocument document = documentWithBody(original)) {
            GuardResult result = runMode(workflow, document, original.length(), "rewrite");

            assertThat(result.success()).isTrue();
            assertThat(result.rewrittenText()).isEqualTo(original);
            assertThat(result.errorMessage()).isEmpty();
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
        return documentWithBodies(body);
    }

    private XWPFDocument documentWithBodies(String... bodies) {
        XWPFDocument document = new XWPFDocument();
        document.createParagraph().createRun().setText("目录");
        document.createParagraph().createRun().setText("第一章 绪论 1");
        document.createParagraph().createRun().setText("第一章 绪论");
        for (String body : bodies) {
            document.createParagraph().createRun().setText(body);
        }
        document.createParagraph().createRun().setText("参考文献");
        return document;
    }

    private GuardResult runMode(
            WorkflowRewriteService workflow,
            XWPFDocument document,
            int originalLength,
            String mode
    ) throws Exception {
        return runModeResults(workflow, document, originalLength, mode).get(0);
    }

    private List<GuardResult> runModeResults(
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
            return results.stream().map(result -> {
                try {
                    return new GuardResult(
                            (boolean) recordValue(result, "success"),
                            (String) recordValue(result, "rewrittenText"),
                            (String) recordValue(result, "errorMessage")
                    );
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }).toList();
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
