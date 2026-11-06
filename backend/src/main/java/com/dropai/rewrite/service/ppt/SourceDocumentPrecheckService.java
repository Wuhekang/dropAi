package com.dropai.rewrite.service.ppt;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class SourceDocumentPrecheckService {
    public static final String CODE="SOURCE_CONTENT_INSUFFICIENT";
    public static final String MESSAGE="当前文件未检测到完整正文或有效章节，请上传完整论文、完整项目报告或完整PPTX文件。";
    private static final List<String> NON_BODY=List.of("封面","目录","摘要","abstract","关键词","参考文献","致谢","附录","开题答辩","学生信息","任务审批","综合评价");

    public Report check(PptDocumentParser.ParsedDocument document){
        long meaningful=document.blocks().stream().map(SourceDocumentPrecheckService::plain).filter(x->x.length()>=40).count();
        long chapters=document.headings().stream().filter(this::bodyHeading).count();
        boolean passed=!document.title().isBlank()&&meaningful>=2&&chapters>=2&&document.characterCount()>=200;
        return new Report(passed,passed?"OK":CODE,passed?"源文件预检通过":MESSAGE,(int)chapters,(int)meaningful,document.characterCount());
    }
    public Report require(PptDocumentParser.ParsedDocument document){Report report=check(document);if(!report.passed())throw new InsufficientSourceException(report);return report;}
    private boolean bodyHeading(String value){String v=plain(value).toLowerCase();return v.length()>=2&&NON_BODY.stream().noneMatch(v::contains);}
    private static String plain(String value){return value==null?"":value.replaceFirst("^\\[第\\d+页]\\s*","").replaceAll("\\s+"," ").trim();}
    public record Report(boolean passed,String code,String message,int validChapterCount,int meaningfulBlockCount,int characterCount){}
    public static class InsufficientSourceException extends IllegalArgumentException {private final Report report;public InsufficientSourceException(Report report){super(report.message());this.report=report;}public Report report(){return report;}}
}
