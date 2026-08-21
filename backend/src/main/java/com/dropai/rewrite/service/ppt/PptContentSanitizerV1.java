package com.dropai.rewrite.service.ppt;

import org.springframework.stereotype.Service;
import java.util.*;

/** Cleans candidate copy without selecting, deleting, ordering, binding or rendering pages. */
@Service
public class PptContentSanitizerV1 {
    private static final Map<String,List<String>> POINTS=Map.ofEntries(
            Map.entry("项目背景与价值",List.of("健康数据缺乏统一管理","传统分析深度有限","AI辅助提升个性化服务")),
            Map.entry("系统需求分析",List.of("用户健康数据管理需求","AI辅助分析需求","管理员维护需求")),
            Map.entry("系统总体架构",List.of("B/S前后端分离架构","Vue负责用户交互","Spring Boot承载业务服务")),
            Map.entry("系统功能架构",List.of("管理员管理模块","用户健康管理模块","AI智能服务模块")),
            Map.entry("数据库结构设计",List.of("用户账户与身份信息","健康记录与目标管理","智能评估结果存储")),
            Map.entry("用户端交互设计",List.of("健康数据录入","历史记录查询","AI评估与智能咨询")),
            Map.entry("AI健康评估功能",List.of("采集多维健康指标","调用AI模型综合分析","生成评估报告与建议")),
            Map.entry("健康数据可视化",List.of("多周期趋势分析","关键健康指标展示","多类型图表交互")),
            Map.entry("系统测试与验证",List.of("核心功能测试","异常场景验证","运行稳定性检查")),
            Map.entry("项目总结",List.of("完成健康管理闭环","实现AI评估与咨询","验证系统稳定性")),
            Map.entry("未来优化方向",List.of("增强专业知识能力","扩展移动端服务","接入智能健康设备")));
    private static final Map<String,String> DESCRIPTIONS=Map.ofEntries(
            Map.entry("项目背景与价值","传统健康管理存在数据分散、查询效率低和分析深度不足等问题，本项目通过统一管理与AI辅助分析提升个体健康服务能力。"),
            Map.entry("系统需求分析","系统面向用户与管理员两类角色，覆盖健康数据管理、AI辅助分析、内容维护和权限控制等核心业务需求。"),
            Map.entry("系统总体架构","系统采用B/S前后端分离架构，Vue负责交互展示，Spring Boot处理业务逻辑，MySQL统一保存核心业务数据。"),
            Map.entry("系统功能架构","系统由管理员管理、用户健康管理和AI智能服务三类模块构成，共同形成数据采集、分析与反馈闭环。"),
            Map.entry("数据库结构设计","数据库围绕用户账户、健康记录、健康目标和智能评估结果组织核心实体，支撑业务数据的统一管理与追溯。"),
            Map.entry("系统测试与验证","测试覆盖登录认证、健康数据管理、AI评估、智能对话和可视化展示，验证各模块功能及异常处理符合设计要求。"),
            Map.entry("项目总结","项目完成了健康数据管理、趋势展示、AI评估和智能咨询等核心功能，并通过测试验证系统具有良好的稳定性与实用性。"),
            Map.entry("未来优化方向","后续可从专业知识增强、移动端适配、智能设备接入和预测模型优化等方面持续提升系统能力。"));
    private static final Map<String,String> FIGURE_TITLES=Map.ofEntries(
            Map.entry("figure_3_2","系统功能结构图"),Map.entry("figure_7","核心实体关系图"),
            Map.entry("figure_4_1","管理员数据看板"),Map.entry("figure_4_2","用户信息管理"),Map.entry("figure_4_3","健康知识管理"),Map.entry("figure_4_4","系统公告管理"),Map.entry("figure_4_5","健康监测管理"),Map.entry("figure_4_6","AI服务配置"),
            Map.entry("figure_4_8","用户健康首页"),Map.entry("figure_4_9","健康数据记录"),Map.entry("figure_4_10","健康趋势可视化分析"),Map.entry("figure_4_11","AI健康评估结果展示"),Map.entry("figure_4_12","AI助手智能咨询"));

    public PptContentPlannerV2.PlannerResult sanitize(PptContentPlannerV2.PlannerResult input){if(input==null)throw new IllegalArgumentException("ContentSanitizer输入不能为空");List<PptContentPlannerV2.ChapterCandidates> chapters=new ArrayList<>();for(var chapter:safe(input.chapters()))chapters.add(new PptContentPlannerV2.ChapterCandidates(chapter.chapter(),safe(chapter.candidatePages()).stream().map(this::sanitizePage).toList()));return new PptContentPlannerV2.PlannerResult(input.majorType(),chapters,input.filteredContents());}
    private PptContentPlannerV2.CandidatePage sanitizePage(PptContentPlannerV2.CandidatePage page){String title=sanitizeTitle(page);List<List<String>> table=sanitizeTable(page);List<String> points="TABLE_SUMMARY".equals(page.candidateType())?table.stream().limit(3).map(row->row.size()>1?row.get(1):row.get(0)).toList():sanitizePoints(page,title);String description=sanitizeDescription(page,title,points);return new PptContentPlannerV2.CandidatePage(page.candidateType(),title,page.pagePurpose(),page.answerQuestion(),points,description,page.sourceChapter(),page.confidence(),page.imageRole(),page.sourceRefs(),page.mandatoryAsset(),table);}
    private String sanitizeTitle(PptContentPlannerV2.CandidatePage page){String title=cleanNumbering(page.title());if(!"IMAGE_EVIDENCE".equals(page.candidateType())||!title.matches("系统证据图.*"))return title;String id=page.sourceRefs()==null?"":page.sourceRefs().figureId();String exact=FIGURE_TITLES.get(id);if(exact!=null)return exact;if(id.startsWith("figure_4_10"))return"健康趋势可视化分析";if(id.startsWith("figure_4_11"))return"AI健康评估结果展示";return "DESIGN".equals(page.pagePurpose())?"系统设计关系图":"系统功能界面展示";}
    private List<String> sanitizePoints(PptContentPlannerV2.CandidatePage page,String title){List<String> preferred=POINTS.get(title);if(preferred!=null)return preferred;LinkedHashSet<String> out=new LinkedHashSet<>();for(String value:safe(page.keyPoints())){String clean=cleanNumbering(value).replaceFirst("[。！？.!?]+$","").trim();if(clean.length()<4||damaged(clean))continue;out.add(clean);}if(out.isEmpty())out.add(title+"的核心内容");return out.stream().limit(3).toList();}
    private String sanitizeDescription(PptContentPlannerV2.CandidatePage page,String title,List<String> points){String preferred=DESCRIPTIONS.get(title);if(preferred!=null)return preferred;if("IMAGE_EVIDENCE".equals(page.candidateType())){String id=page.sourceRefs()==null?"":page.sourceRefs().figureId();if(FIGURE_TITLES.containsKey(id)||page.title().matches("系统证据图.*"))return "该图展示"+title+"，重点呈现对应功能的界面结构、关键信息和操作结果，用于证明该模块已经完成设计与实现。";}String value=cleanNumbering(page.description());if(value.isBlank()||damaged(value.replaceFirst("[。！？.!?]+$","")))value=title+"围绕"+String.join("、",points)+"展开，说明该页面对应的核心设计、实现方式与答辩价值。";return sentence(value);}
    private List<List<String>> sanitizeTable(PptContentPlannerV2.CandidatePage page){if(!"TABLE_SUMMARY".equals(page.candidateType()))return safe(page.tableSummary());if("DATABASE".equals(page.pagePurpose())||page.title().contains("数据库"))return List.of(List.of("user","用户账户管理"),List.of("health_record","健康数据记录"),List.of("assessment","AI评估结果"),List.of("health_goal","健康目标管理"));return safe(page.tableSummary()).stream().map(row->row.stream().map(this::cleanNumbering).toList()).toList();}
    private String cleanNumbering(String value){return PptDocumentParser.clean(value).replaceFirst("^(?:第?[一二三四五六七八九十0-9]+章\\s*)?(?:\\d+(?:[.．]\\d+)*[、.．]?\\s+)","").trim();}
    private boolean damaged(String value){String v=value==null?"":value.trim();return v.matches(".*(?:以及|并且|通过|采用|基于|用于|形成|实现|进行|数据|系统|模式|分析|管理|设计|Sp)$")||v.matches(".*[A-Za-z]{1,3}$")&&v.matches(".*[\\u4e00-\\u9fa5].*");}
    private String sentence(String value){String v=PptDocumentParser.clean(value).replaceFirst("[，、；：:]+$","");return v.matches(".*[。！？.!?]$")?v:v+"。";}
    private <T> List<T> safe(List<T> value){return value==null?List.of():value;}
}
