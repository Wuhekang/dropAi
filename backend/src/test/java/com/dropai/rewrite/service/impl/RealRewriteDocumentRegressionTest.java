package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Optional local regression checks against the two supplied mechanical manuscripts. */
class RealRewriteDocumentRegressionTest {

    @Test
    void suppliedFoodConveyorDocumentHasFullBodyCoverageAndLeakIsBlocked() throws Exception {
        Path path = requiredPath("dropai.test.food.docx");
        audit(path, 180, 14_000);
    }

    @Test
    void suppliedYx132DocumentHasFullBodyCoverageAndLeakIsBlocked() throws Exception {
        Path path = requiredPath("dropai.test.yx.docx");
        audit(path, 360, 17_000);
    }

    @Test
    void suppliedBamaCostDocumentKeepsEditableBodyCoverage() throws Exception {
        auditCleanDocument(requiredPath("dropai.test.bama.docx"), 45, 5_400);
    }

    @Test
    void suppliedGuangxiCostDocumentKeepsEditableBodyCoverage() throws Exception {
        auditCleanDocument(requiredPath("dropai.test.guangxi.docx"), 50, 5_800);
    }

    private void auditCleanDocument(Path path, int minimumTargets, int minimumCharacters) throws Exception {
        DocumentRewriteServiceImpl service = new DocumentRewriteServiceImpl(
                null, null, null, null, null, null, null);
        try (InputStream stream = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(stream)) {
            DocumentRewriteJobVO job = new DocumentRewriteJobVO();
            job.setMode("rewrite");
            job.setPlatform("GENERAL");
            Method collect = DocumentRewriteServiceImpl.class.getDeclaredMethod(
                    "collectRewriteTargets", DocumentRewriteJobVO.class, XWPFDocument.class);
            collect.setAccessible(true);
            List<?> targets = (List<?>) collect.invoke(service, job, document);
            int characters = 0;
            for (Object target : targets) {
                Method textMethod = target.getClass().getDeclaredMethod("text");
                textMethod.setAccessible(true);
                String paragraph = (String) textMethod.invoke(target);
                characters += paragraph.length();
                assertThat(RewriteQualityGate.assess(paragraph, paragraph).accepted()).isTrue();
            }
            assertThat(targets.size()).as("rewrite targets in %s", path.getFileName())
                    .isGreaterThanOrEqualTo(minimumTargets);
            assertThat(characters).as("rewrite characters in %s", path.getFileName())
                    .isGreaterThanOrEqualTo(minimumCharacters);

            Method finalGuard = DocumentRewriteServiceImpl.class.getDeclaredMethod(
                    "assertNoUnresolvedPlaceholders", XWPFDocument.class);
            finalGuard.setAccessible(true);
            finalGuard.invoke(service, document);
        } finally {
            service.shutdown();
        }
    }

    private void audit(Path path, int minimumTargets, int minimumCharacters) throws Exception {
        DocumentRewriteServiceImpl service = new DocumentRewriteServiceImpl(
                null, null, null, null, null, null, null);
        try (InputStream stream = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(stream)) {
            DocumentRewriteJobVO job = new DocumentRewriteJobVO();
            job.setMode("rewrite");
            job.setPlatform("GENERAL");

            Method collect = DocumentRewriteServiceImpl.class.getDeclaredMethod(
                    "collectRewriteTargets", DocumentRewriteJobVO.class, XWPFDocument.class);
            collect.setAccessible(true);
            List<?> targets = (List<?>) collect.invoke(service, job, document);
            int characters = 0;
            for (Object target : targets) {
                Method text = target.getClass().getDeclaredMethod("text");
                text.setAccessible(true);
                characters += ((String) text.invoke(target)).length();
            }
            assertThat(targets.size())
                    .as("rewrite target count for %s (characters=%s)", path.getFileName(), characters)
                    .isGreaterThanOrEqualTo(minimumTargets);
            assertThat(characters)
                    .as("rewrite target characters for %s (targets=%s)", path.getFileName(), targets.size())
                    .isGreaterThanOrEqualTo(minimumCharacters);

            Method finalGuard = DocumentRewriteServiceImpl.class.getDeclaredMethod(
                    "assertNoUnresolvedPlaceholders", XWPFDocument.class);
            finalGuard.setAccessible(true);
            try {
                finalGuard.invoke(service, document);
                throw new AssertionError("fixture should contain the historical leaked placeholder");
            } catch (InvocationTargetException exception) {
                assertThat(exception.getCause())
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("未还原的保护占位符");
            }
        } finally {
            service.shutdown();
        }
    }

    private Path requiredPath(String property) {
        String value = System.getProperty(property, "");
        Assumptions.assumeTrue(!value.isBlank(), property + " is not configured");
        Path path = Path.of(value);
        Assumptions.assumeTrue(Files.isRegularFile(path), path + " does not exist");
        return path;
    }
}
