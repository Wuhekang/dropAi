package com.dropai.rewrite.service.ppt;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PptAutoVisualDirectorTest {
    private final PptAutoVisualDirector director = new PptAutoVisualDirector();

    @Test
    void sameFrozenProjectInputAlwaysProducesSameVisualDirection() {
        Map<String, Object> project = project("project-stable", "软件工程",
                "基于Spring Boot的健康管理系统设计与实现", 25, 40);

        var first = director.direct(project);
        var second = director.direct(new LinkedHashMap<>(project));

        assertEquals(first, second);
        assertEquals(PptAutoVisualDirector.LAYOUT_STRATEGY, first.layoutStrategy());
        assertEquals(PptAutoVisualDirector.PALETTE_STRATEGY, first.paletteStrategy());
        assertTrue(first.candidatePool().contains(first.templatePackId()));
    }

    @Test
    void newProjectsDoNotCollapseToOneFixedTemplate() {
        Set<String> selected = new LinkedHashSet<>();
        for (int index = 1; index <= 32; index++) {
            selected.add(director.direct(project("project-" + index, "护理学",
                    "患者营养护理研究进展", 10, 18)).templatePackId());
        }

        assertTrue(selected.size() >= 3, "32个新任务应产生多套可信视觉方向: " + selected);
        assertFalse(selected.contains("AI_RECOMMEND"));
    }

    @Test
    void domainPoolsDoNotOfferElectricalCollegeBrandingToNursingDocuments() {
        var direction = director.direct(project("nursing-project", "护理学",
                "胃癌患者术后营养护理研究进展", 10, 18));

        assertEquals("NURSING_MEDICAL", direction.domain());
        assertFalse(direction.candidatePool().contains(PptTemplateService.ELECTRICAL_COLLEGE_DEFENSE_V1));
    }

    @Test
    void intelligentBookRecommendationGetsDecoratedComputerVisuals() {
        var direction = director.direct(project("safety-books", "",
                "安全类图书智能问答与推荐系统的设计与实现", 0, 13));

        assertEquals("COMPUTER_ENGINEERING", direction.domain());
        assertFalse(direction.candidatePool().contains(PptTemplateService.ACADEMIC_PURPLE));
        assertTrue(Set.of(PptTemplateService.BEAR_MINIMAL_TAN_V1,
                PptTemplateService.SMALL_BEAR_WATERCOLOR_BLUE_FULL_V1).contains(direction.templatePackId()));
    }

    @Test
    void automaticDirectionNeverUsesUndecoratedAcademicBaseline() {
        for (String topic : new String[]{"安全类图书智能问答与推荐系统", "患者营养护理", "财务偿债能力", "电气设备设计", "一般学术研究"}) {
            var direction = director.direct(project(topic, "", topic, 0, 13));
            assertFalse(direction.candidatePool().contains(PptTemplateService.ACADEMIC_PURPLE), topic);
        }
    }

    private Map<String, Object> project(String id, String major, String topic, int images, int slides) {
        Map<String, Object> project = new LinkedHashMap<>();
        project.put("id", id);
        project.put("major", major);
        project.put("topic", topic);
        project.put("source_file_name", topic + ".docx");
        project.put("analysis_json", "{}");
        project.put("image_count", images);
        project.put("target_slide_count", slides);
        return project;
    }
}
