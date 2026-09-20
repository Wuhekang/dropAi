package com.dropai.rewrite.service.ppt;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PptDocumentParserTest {
    @TempDir Path temp;

    @Test
    void recognizesOpeningProposalHeadingsEvenWhenWordUsesNormalStyle() throws Exception {
        Path source=temp.resolve("opening-proposal.docx");
        try(XWPFDocument document=new XWPFDocument()){
            document.createParagraph().createRun().setText("题目：新能源企业偿债能力研究");
            var firstHeading=document.createParagraph().createRun();firstHeading.setText("一、课题研究现状");firstHeading.setFontSize(15);
            document.createParagraph().createRun().setText("国外研究已经从单一财务比率分析转向流动性、杠杆、现金流和债务期限结构的综合评价，并形成了较为成熟的分析框架。");
            var secondHeading=document.createParagraph().createRun();secondHeading.setText("二、选题目的和意义");secondHeading.setFontSize(15);
            document.createParagraph().createRun().setText("本研究结合资产负债表与现金流量指标，从短期和长期两个期限维度评价企业偿债能力，并为融资结构优化提供参考依据。");
            var thirdHeading=document.createParagraph().createRun();thirdHeading.setText("三、课题研究基本内容");thirdHeading.setFontSize(15);
            document.createParagraph().createRun().setText("研究将依次完成现状评价、问题识别、影响因素分析与对策设计，形成逻辑闭环并保证结论能够被公开数据验证。");
            try(var out=Files.newOutputStream(source)){document.write(out);}
        }

        var parsed=new PptDocumentParser().parse(source,temp.resolve("assets"));
        assertThat(parsed.headings()).contains("一、课题研究现状","二、选题目的和意义","三、课题研究基本内容");
        assertThat(new SourceDocumentPrecheckService().check(parsed).passed()).isTrue();
    }

    @Test
    void adapterKeepsTheLastCoherentMainChapterSequence() {
        List<String> headings=List.of("第一章 任务依据","第二章 文献准备","第三章 工作要求","第四章 进度安排",
                "第一章 绪论","第二章 需求分析","第三章 系统设计","第四章 总结");
        List<String> blocks=headings.stream().flatMap(title->java.util.stream.Stream.of(title,title+"正文内容用于章节聚合验证。")).toList();
        var parsed=new PptDocumentParser.ParsedDocument("系统论文",headings,blocks,List.of(),0,blocks.stream().mapToInt(String::length).sum());

        var input=new PptContentPlannerV2InputAdapter().fromParsedDocument(parsed,"computer");
        assertThat(input.chapters()).extracting(PptContentPlannerV2.SourceChapter::title)
                .containsExactly("第一章 绪论","第二章 需求分析","第三章 系统设计","第四章 总结");
    }
}
