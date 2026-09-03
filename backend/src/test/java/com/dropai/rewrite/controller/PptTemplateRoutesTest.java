package com.dropai.rewrite.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class PptTemplateRoutesTest {
    @Test
    void formalTemplateSelectionRoutesMatchTheExistingFrontendContract() throws Exception {
        Method list = PptController.class.getMethod("templates");
        Method recommend = PptController.class.getMethod("recommendTemplate", String.class);
        Method select = PptController.class.getMethod("selectTemplate", String.class, Map.class);

        assertArrayEquals(new String[]{"/templates"}, list.getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[]{"/projects/{id}/template/recommend"},
                recommend.getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[]{"/projects/{id}/template"},
                select.getAnnotation(PutMapping.class).value());
    }
}
