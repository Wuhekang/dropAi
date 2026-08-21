package com.dropai.rewrite;

import com.dropai.rewrite.service.ppt.PptDocumentParser;
import com.dropai.rewrite.service.ppt.SourceDocumentPrecheckService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PptRealSourceRegressionTest {
    @Test void chineseCustomHeadingStylesPassCompleteSourcePrecheck()throws Exception{
        Path source=Path.of(System.getProperty("ppt.real.docx","D:/废纸/自己的/6022203537_高瑞康/6022203537_高瑞康_基于Spring Boot的个人健康管理系统的设计与实现最终稿.docx"));
        Assumptions.assumeTrue(Files.isRegularFile(source));
        var parsed=new PptDocumentParser().parse(source,Path.of("target","ppt-real-source-assets"));
        assertTrue(parsed.headings().size()>=2);
        assertEquals(29,parsed.totalImageCount(),"真实论文的图片总数应被完整扫描");
        assertTrue(parsed.filteredAssetCount()>0,"校徽、首章或装饰图片应被过滤");
        assertTrue(parsed.assets().size()>8,"有效论文插图不应再被固定的8页上限截断");
        assertTrue(new SourceDocumentPrecheckService().require(parsed).passed());
    }
}
