package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.service.WorkflowRewriteService;
import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NativeHumanizeRoutingTest {

    @Test
    void documentHumanizeCarriesPlatformAndDepthWhileRewriteAndDoubleDoNot() {
        DocumentRewriteServiceImpl service = new DocumentRewriteServiceImpl(
                null, null, null, null, null, null, null);

        service.bindHumanizeBase("paperpass", "humanize");
        assertThat(service.workflowRequestType(job("paperpass", "humanize", "Paper-Pass"), "humanize"))
                .isEqualTo("humanize@PAPERPASS");

        service.bindHumanizeBase("cnki-deep", "深度降低AI写作痕迹");
        assertThat(service.workflowRequestType(job("cnki-deep", "humanize", "知网"), "humanize"))
                .isEqualTo("深度降低AI写作痕迹@CNKI");

        service.bindHumanizeBase("gezida-deep", "深度降低AI写作痕迹");
        assertThat(service.workflowRequestType(job("gezida-deep", "humanize", "格子达"), "humanize"))
                .isEqualTo("深度降低AI写作痕迹@GEZIDA");

        assertThat(service.workflowRequestType(job("blank", "humanize", " "), "humanize"))
                .isEqualTo("humanize");
        assertThat(service.workflowRequestType(job("daya", "humanize", "大雅"), "humanize"))
                .isEqualTo("humanize");
        service.bindHumanizeBase("full", "FULL_AI_REDUCE");
        assertThat(service.workflowRequestType(job("full", "humanize", "WEIPU"), "humanize"))
                .isEqualTo("humanize@WEIPU");
        service.bindHumanizeBase("precise", "PRECISE_AI_REDUCE");
        assertThat(service.workflowRequestType(job("precise", "humanize", "CNKI"), "humanize"))
                .isEqualTo("humanize@CNKI");

        assertThat(service.workflowRequestType(job("rewrite-new", "rewrite", "PAPERPASS"), "rewrite"))
                .isEqualTo("rewrite");
        assertThat(service.workflowRequestType(job("rewrite-old", "rewrite", "CNKI"), "rewrite"))
                .isEqualTo("rewrite@CNKI");
        assertThat(service.workflowRequestType(job("double-new", "double", "Turnitin"), "rewrite"))
                .isEqualTo("rewrite");
        assertThat(service.workflowRequestType(job("double-new", "double", "Turnitin"), "humanize"))
                .isEqualTo("humanize-shared");
        assertThat(service.workflowRequestType(job("double-old", "double", "WEIPU"), "rewrite"))
                .isEqualTo("rewrite@WEIPU");
        assertThat(service.workflowRequestType(job("double-old", "double", "WEIPU"), "humanize"))
                .isEqualTo("humanize-shared@WEIPU");
    }

    @Test
    void textHumanizeAcceptsNewPlatformsAndRewriteKeepsTheOldCodes() throws Exception {
        RewriteRecordServiceImpl service = new RewriteRecordServiceImpl(
                mock(WorkflowRewriteService.class), new ObjectMapper());

        assertThat(rewriteType(service, "humanize", "PaperPass")).isEqualTo("humanize@PAPERPASS");
        assertThat(rewriteType(service, "深度降低AI写作痕迹", "知网")).isEqualTo("深度降低AI写作痕迹@CNKI");
        assertThat(rewriteType(service, "降低AI写作痕迹", "朱雀")).isEqualTo("降低AI写作痕迹@ZHUQUE");
        assertThat(rewriteType(service, "智能降AI", "Turnitin")).isEqualTo("智能降AI@TURNITIN");
        assertThat(rewriteType(service, "humanize", "大雅")).isEqualTo("humanize");
        assertThat(rewriteType(service, "humanize", null)).isEqualTo("humanize");
        assertThat(rewriteType(service, "rewrite", "PaperPass")).isEqualTo("rewrite");
        assertThat(rewriteType(service, "rewrite", "CNKI")).isEqualTo("rewrite@CNKI");
        assertThat(rewriteType(service, "双降", "GEZIDA")).isEqualTo("双降@GEZIDA");
        assertThat(rewriteType(service, "双降", "格子达")).isEqualTo("双降");
        assertThat(rewriteType(service, "double", "PAPERYY")).isEqualTo("double");

        assertThat(displayType(service, "humanize", "wanfang")).isEqualTo("humanize / 万方");
        assertThat(displayType(service, "rewrite", "PaperPass")).isEqualTo("rewrite");
        assertThat(displayType(service, "rewrite", "GEZIDA")).isEqualTo("rewrite / 格子达");
    }

    private static DocumentRewriteJobVO job(String jobId, String mode, String platform) {
        DocumentRewriteJobVO job = new DocumentRewriteJobVO();
        job.setJobId(jobId);
        job.setMode(mode);
        job.setPlatform(platform);
        return job;
    }

    private static String rewriteType(RewriteRecordServiceImpl service, String rewriteType, String platform) throws Exception {
        Method method = RewriteRecordServiceImpl.class.getDeclaredMethod(
                "rewriteTypeWithPlatform", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, rewriteType, platform);
    }

    private static String displayType(RewriteRecordServiceImpl service, String rewriteType, String platform) throws Exception {
        Method method = RewriteRecordServiceImpl.class.getDeclaredMethod(
                "displayRewriteType", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, rewriteType, platform);
    }
}
