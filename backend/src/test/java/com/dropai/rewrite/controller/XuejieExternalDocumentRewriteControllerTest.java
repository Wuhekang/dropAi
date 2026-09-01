package com.dropai.rewrite.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class XuejieExternalDocumentRewriteControllerTest {

    @Test
    void externalUploadHasASeparateRouteFromTheDefaultController() throws Exception {
        RequestMapping root = XuejieExternalDocumentRewriteController.class.getAnnotation(RequestMapping.class);
        Method upload = XuejieExternalDocumentRewriteController.class.getMethod("upload",
                org.springframework.web.multipart.MultipartFile.class, String.class, String.class, String.class);
        PostMapping post = upload.getAnnotation(PostMapping.class);

        assertThat(root.value()).containsExactly("/api/document/rewrite/external");
        assertThat(post.value()).containsExactly("/upload");
        assertThat(DocumentRewriteController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/document/rewrite");
    }
}
