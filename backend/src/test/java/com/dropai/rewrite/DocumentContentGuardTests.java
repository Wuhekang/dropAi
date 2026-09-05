package com.dropai.rewrite;

import com.dropai.rewrite.service.impl.DocumentRewriteServiceImpl;
import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentContentGuardTests {

    private final DocumentRewriteServiceImpl service = new DocumentRewriteServiceImpl(null, null, null, null, null, null, null);

    @Test
    void detectsCodeLikeFragmentsAsProtectedContent() throws Exception {
        assertThat(isTechnicalFragment("@GetMapping(\"/page\")")).isTrue();
        assertThat(isTechnicalFragment("public class UserController {")).isTrue();
        assertThat(isTechnicalFragment("SELECT * FROM user WHERE id = #{id}")).isTrue();
        assertThat(isTechnicalFragment("<template><div>{{ title }}</div></template>")).isTrue();
        assertThat(isTechnicalFragment("private BillService billService;")).isTrue();
        assertThat(isTechnicalFragment("本系统采用Vue完成前端页面开发。")).isFalse();
    }

    @Test
    void detectsCaptionsAndFormulaLinesAsProtectedContent() throws Exception {
        assertThat(isCaptionOrFormulaLine("图 5.1 登录页面")).isTrue();
        assertThat(isCaptionOrFormulaLine("表 4.2 用户信息表")).isTrue();
        assertThat(isCaptionOrFormulaLine("图5-2 TF-IDF与词共现模型运行界面")).isTrue();
        assertThat(isCaptionOrFormulaLine("表5-3两种数据挖掘方法对比")).isTrue();
        assertThat(isCaptionOrFormulaLine("公式 3.1 计费模型")).isTrue();
        assertThat(isCaptionOrFormulaLine("(3.1) y = ax + b")).isTrue();
        assertThat(isCaptionOrFormulaLine("3.1 y = ax + b")).isTrue();
        assertThat(isCaptionOrFormulaLine("2026年9月5日启动系统后，界面如图3-3所示。")).isFalse();
        assertThat(isCaptionOrFormulaLine("该模块主要完成数据统计与展示功能。")).isFalse();
    }

    @Test
    void detectsNumberedHeadingsWithoutSpacesButRejectsNumericSentences() throws Exception {
        assertThat(isHeadingLikeNumberedTitle("1.选题背景分析")).isTrue();
        assertThat(isHeadingLikeNumberedTitle("1.1研究内容")).isTrue();
        assertThat(isHeadingLikeNumberedTitle("1.1.1技术路线")).isTrue();
        assertThat(isHeadingLikeNumberedTitle("2．项目设计")).isTrue();
        assertThat(isHeadingLikeNumberedTitle("2023年公开数据共30000条。")).isFalse();
        assertThat(isHeadingLikeNumberedTitle("1.5倍增长属于数据描述。")).isFalse();
    }

    @Test
    void startsParagraphProcessingAtAbstractAndDoesNotStopAtFrontMatterDeclaration() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("封面文字");
            document.createParagraph().createRun().setText("学位论文原创性声明");
            document.createParagraph().createRun().setText("声明内容不应处理");
            document.createParagraph().createRun().setText("摘要");
            document.createParagraph().createRun().setText("这是需要处理的摘要正文段落。");
            document.createParagraph().createRun().setText("关键词：测试");
            document.createParagraph().createRun().setText("第一章 绪论");
            document.createParagraph().createRun().setText("这是需要处理的第一章正文段落。");
            document.createParagraph().createRun().setText("参考文献");
            document.createParagraph().createRun().setText("参考内容不应处理");

            Method method = DocumentRewriteServiceImpl.class.getDeclaredMethod(
                    "collectRewriteTargets", DocumentRewriteJobVO.class, XWPFDocument.class
            );
            method.setAccessible(true);
            DocumentRewriteJobVO job = new DocumentRewriteJobVO();
            job.setMode("humanize");

            List<?> targets = (List<?>) method.invoke(service, job, document);

            assertThat(targets).hasSize(2);
        }
    }

    @Test
    void reentersBodyAfterKeywordsAtNoSpaceNumberedHeading() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("摘要");
            document.createParagraph().createRun().setText("这是需要处理的摘要正文段落。");
            document.createParagraph().createRun().setText("关键词：测试");
            document.createParagraph().createRun().setText("1.选题背景分析");
            document.createParagraph().createRun().setText("这是需要处理的第一章正文段落。");
            document.createParagraph().createRun().setText("参考文献");

            List<?> targets = collectRewriteTargets(document);

            assertThat(targets).hasSize(2);
        }
    }

    @Test
    void fallsBackToCatalogAndStartsAtFirstBodyHeadingWhenAbstractIsMissing() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("封面文字");
            document.createParagraph().createRun().setText("目录");
            document.createParagraph().createRun().setText("第一章 绪论 1");
            document.createParagraph().createRun().setText("第一章 绪论");
            document.createParagraph().createRun().setText("这是目录后的正文段落。");
            document.createParagraph().createRun().setText("参考文献");

            Method method = DocumentRewriteServiceImpl.class.getDeclaredMethod(
                    "collectRewriteTargets", DocumentRewriteJobVO.class, XWPFDocument.class
            );
            method.setAccessible(true);
            DocumentRewriteJobVO job = new DocumentRewriteJobVO();
            job.setMode("humanize");

            List<?> targets = (List<?>) method.invoke(service, job, document);

            assertThat(targets).hasSize(1);
        }
    }

    @Test
    void startsAtNoSpaceBodyHeadingWithoutTreatingCatalogOrCaptionsAsBody() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("封面文字");
            document.createParagraph().createRun().setText("目录");
            addCatalogEntry(document, "1.选题背景分析", "3");
            addCatalogEntry(document, "1.1研究内容", "4");
            document.createParagraph().createRun().setText("1.选题背景分析");
            document.createParagraph().createRun().setText("这是第一章需要处理的正文段落。");
            document.createParagraph().createRun().setText("1.1研究内容");
            document.createParagraph().createRun().setText("这是第二个需要处理的正文段落。");
            document.createParagraph().createRun().setText("图5-2模型运行界面");
            document.createParagraph().createRun().setText("参考文献");

            List<?> targets = collectRewriteTargets(document);

            assertThat(targets).hasSize(2);
        }
    }

    private boolean isTechnicalFragment(String text) throws Exception {
        Method method = DocumentRewriteServiceImpl.class.getDeclaredMethod("isTechnicalFragment", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, text);
    }

    private boolean isCaptionOrFormulaLine(String text) throws Exception {
        Method method = DocumentRewriteServiceImpl.class.getDeclaredMethod("isCaptionOrFormulaLine", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, text);
    }

    private boolean isHeadingLikeNumberedTitle(String text) throws Exception {
        Method method = DocumentRewriteServiceImpl.class.getDeclaredMethod("isHeadingLikeNumberedTitle", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, text);
    }

    private List<?> collectRewriteTargets(XWPFDocument document) throws Exception {
        Method method = DocumentRewriteServiceImpl.class.getDeclaredMethod(
                "collectRewriteTargets", DocumentRewriteJobVO.class, XWPFDocument.class
        );
        method.setAccessible(true);
        DocumentRewriteJobVO job = new DocumentRewriteJobVO();
        job.setMode("humanize");
        return (List<?>) method.invoke(service, job, document);
    }

    private void addCatalogEntry(XWPFDocument document, String title, String pageNumber) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(title);
        run.addTab();
        run.setText(pageNumber);
    }
}
