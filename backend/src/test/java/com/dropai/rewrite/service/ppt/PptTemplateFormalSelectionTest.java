package com.dropai.rewrite.service.ppt;

import com.dropai.rewrite.auth.AuthContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PptTemplateFormalSelectionTest {
    private static final long USER_ID = 71L;
    private static final String PROJECT_ID = "project-1";

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void listExposesExactlyTwoTrustedRenderingV1BuiltInsAndMarksUploadsUnsupported() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, Object> uploaded = new LinkedHashMap<>();
        uploaded.put("id", "legacy-upload");
        uploaded.put("template_name", "用户上传模板");
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(uploaded));
        AuthContext.setUserId(USER_ID);

        List<Map<String, Object>> templates = new PptTemplateService(jdbc, new ObjectMapper()).list();

        List<String> trusted = templates.stream()
                .filter(row -> Boolean.TRUE.equals(row.get("renderingV1Supported")))
                .map(row -> String.valueOf(row.get("templatePackId")))
                .toList();
        assertEquals(List.of(PptTemplateService.ACADEMIC_PURPLE,
                PptTemplateService.SMALL_BEAR_WATERCOLOR_BLUE_V1), trusted);
        Map<String, Object> legacy = templates.get(2);
        assertFalse((Boolean) legacy.get("renderingV1Supported"));
        assertFalse((Boolean) legacy.get("trusted"));
        assertEquals(null, legacy.get("templatePackId"));
    }

    @Test
    void thesisRecommendationReturnsConcreteTrustedSmallBearPack() {
        JdbcTemplate jdbc = projectJdbc("OUTLINE_READY");
        AuthContext.setUserId(USER_ID);

        Map<String, Object> result = new PptTemplateService(jdbc, new ObjectMapper())
                .recommend(PROJECT_ID);

        assertEquals(PptTemplateService.SMALL_BEAR_WATERCOLOR_BLUE_V1,
                result.get("templatePackId"));
        assertEquals(result.get("templatePackId"), result.get("templateId"));
        assertEquals(result.get("templatePackId"), result.get("style"));
        assertEquals(true, result.get("renderingV1Supported"));
        assertEquals(true, result.get("trusted"));
    }

    @Test
    void selectingTrustedPackPersistsConcreteIdAndInvalidatesPriorPlanAndOutput() {
        JdbcTemplate jdbc = projectJdbc("PLANNED");
        List<Object[]> updates = new ArrayList<>();
        doAnswer(invocation -> {
            updates.add(invocation.getArguments());
            return 1;
        }).when(jdbc).update(anyString(), any(Object[].class));
        AuthContext.setUserId(USER_ID);

        Map<String, Object> result = new PptTemplateService(jdbc, new ObjectMapper()).select(
                PROJECT_ID, Map.of("templatePackId", PptTemplateService.SMALL_BEAR_WATERCOLOR_BLUE_V1));

        assertEquals(PptTemplateService.SMALL_BEAR_WATERCOLOR_BLUE_V1,
                result.get("templatePackId"));
        assertEquals(true, result.get("planInvalidated"));
        assertEquals(1, updates.size());
        String sql = String.valueOf(updates.get(0)[0]);
        assertTrue(sql.contains("status=CASE WHEN status IN ('PLANNED','SUCCESS','FAILED') THEN 'OUTLINE_READY'"));
        assertTrue(sql.indexOf("current_stage=CASE") < sql.indexOf("status=CASE"),
                "MySQL evaluates assignments left-to-right; status must be updated last");
        assertTrue(sql.contains("status<>'GENERATING'"));
        assertTrue(sql.contains("output_path=NULL"));
        assertEquals(PptTemplateService.SMALL_BEAR_WATERCOLOR_BLUE_V1, updates.get(0)[1]);
        assertEquals(PptTemplateService.SMALL_BEAR_WATERCOLOR_BLUE_V1, updates.get(0)[2]);
        assertTrue(String.valueOf(updates.get(0)[3]).contains("rendering-template-selection.v1"));
        assertTrue(String.valueOf(updates.get(0)[3]).contains(PptTemplateService.SMALL_BEAR_WATERCOLOR_BLUE_V1));
    }

    @Test
    void arbitraryUploadedTemplateCannotEnterFormalRenderingV1() {
        JdbcTemplate jdbc = projectJdbc("OUTLINE_READY");
        AuthContext.setUserId(USER_ID);
        PptTemplateService service = new PptTemplateService(jdbc, new ObjectMapper());

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.select(PROJECT_ID, Map.of("templatePackId", "legacy-upload")));

        assertTrue(failure.getMessage().contains("Rendering V1"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void templateSelectionLosesTheRaceWhenGenerationHasAlreadyClaimedTheProject() {
        JdbcTemplate jdbc = projectJdbc("PLANNED");
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        AuthContext.setUserId(USER_ID);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new PptTemplateService(jdbc, new ObjectMapper()).select(
                        PROJECT_ID,
                        Map.of("templatePackId", PptTemplateService.SMALL_BEAR_WATERCOLOR_BLUE_V1)));

        assertTrue(failure.getMessage().contains("正在生成"));
    }

    private JdbcTemplate projectJdbc(String status) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.startsWith("SELECT p.*")) {
                Map<String, Object> project = new LinkedHashMap<>();
                project.put("id", PROJECT_ID);
                project.put("status", status);
                project.put("topic", "基于Spring Boot的个人健康管理系统设计与实现");
                project.put("source_file_name", "健康管理系统最终论文.docx");
                project.put("major", "软件工程");
                project.put("target_slide_count", 16);
                project.put("image_count", 25);
                return List.of(project);
            }
            return List.of();
        });
        return jdbc;
    }
}
