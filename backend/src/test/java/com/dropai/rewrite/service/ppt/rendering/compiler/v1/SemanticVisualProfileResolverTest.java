package com.dropai.rewrite.service.ppt.rendering.compiler.v1;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SemanticVisualProfileResolverTest {
    private final SemanticVisualProfileResolver resolver = new SemanticVisualProfileResolver();

    @Test
    void wordsDriveDifferentThemeSafeColorsAndShapes() {
        var problem = resolver.resolve(page("核心问题与风险", "PROBLEM", "现有流程存在明显不足"));
        var method = resolver.resolve(page("研究方法与技术路线", "METHOD", "构建分析模型"));
        var evidence = resolver.resolve(page("测试结果与验证", "TEST", "验证系统效果"));

        assertEquals("colors.state.danger", problem.primaryColorToken());
        assertEquals("RECTANGLE", problem.markerShape());
        assertEquals("colors.state.info", method.primaryColorToken());
        assertEquals("ROUNDED_RECTANGLE", method.markerShape());
        assertEquals("colors.state.success", evidence.primaryColorToken());
        assertEquals("ELLIPSE", evidence.markerShape());
        assertNotEquals(problem.primaryColorToken(), method.primaryColorToken());
        assertNotEquals(method.markerShape(), evidence.markerShape());
    }

    @Test
    void sameTextAlwaysResolvesToTheSameProfile() {
        ObjectNode page = page("一般性研究内容", "", "说明研究内容与实施安排");
        assertEquals(resolver.resolve(page), resolver.resolve(page.deepCopy()));
    }

    private ObjectNode page(String title, String purpose, String description) {
        ObjectNode page = JsonNodeFactory.instance.objectNode();
        page.put("title", title);
        page.put("pagePurpose", purpose);
        page.put("description", description);
        page.put("answerQuestion", "这一页回答什么问题？");
        page.putArray("keyPoints").add(description);
        return page;
    }
}
