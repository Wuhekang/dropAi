package com.dropai.rewrite.service.ppt;

import com.dropai.rewrite.config.PptProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PptAiService {
    private final PptProperties properties; private final RestClient client; private final ObjectMapper mapper;
    public PptAiService(PptProperties properties, RestClient.Builder builder, ObjectMapper mapper){this.properties=properties;this.client=builder.build();this.mapper=mapper;}

    public AiOutline createOutline(String topic, PptDocumentParser.ParsedDocument document) {
        if (!properties.configured()) return new AiOutline(fallback(document),false,"NOT_CONFIGURED");
        try {
            Map<String,Object> request=new LinkedHashMap<>(); request.put("model",properties.model()); request.put("stream",false); request.put("max_output_tokens",4096);
            request.put("input",prompt(topic,document));
            JsonNode root=client.post().uri(endpoint()).header(HttpHeaders.AUTHORIZATION,"Bearer "+properties.apiKey()).contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(JsonNode.class);
            List<OutlineItem> parsed=parse(outputText(root));
            return parsed.size()>=4?new AiOutline(parsed,true,"SUCCESS"):new AiOutline(fallback(document),true,"INVALID_OUTLINE_FALLBACK");
        } catch(Exception e) { return new AiOutline(fallback(document),false,"CALL_FAILED"); }
    }

    public String createEnglishTitle(String chineseTitle){
        if(!properties.configured()||chineseTitle==null||chineseTitle.isBlank())return "Academic Presentation";
        try{Map<String,Object> request=new LinkedHashMap<>();request.put("model",properties.model());request.put("stream",false);request.put("max_output_tokens",256);request.put("input","将以下中文学术题名翻译为简洁、准确的英文题名。只返回英文题名，不加引号，不编造信息："+chineseTitle);JsonNode root=client.post().uri(endpoint()).header(HttpHeaders.AUTHORIZATION,"Bearer "+properties.apiKey()).contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(JsonNode.class);String title=outputText(root).replaceAll("^[\\s\"']+|[\\s\"']+$","");return PptDocumentParser.shorten(title,180);}catch(Exception e){return "Academic Presentation";}
    }

    private String prompt(String topic,PptDocumentParser.ParsedDocument d){
        String source=String.join("\n",d.blocks()); if(source.length()>18000)source=source.substring(0,18000);
        return "你是学术答辩PPT结构设计师。严格依据来源内容生成4至6个不重复目录，不得编造事实。返回纯JSON数组，每项字段title、description、slides。"
            +"每个title不超过12字，description不超过40字。题目："+topic+"\n识别标题："+d.headings()+"\n来源内容：\n"+source;
    }
    private List<OutlineItem> parse(String text)throws Exception{
        int a=text.indexOf('['),b=text.lastIndexOf(']'); if(a<0||b<=a)return List.of(); JsonNode array=mapper.readTree(text.substring(a,b+1)); List<OutlineItem> result=new ArrayList<>();
        for(JsonNode n:array){String title=PptDocumentParser.shorten(n.path("title").asText(),20);if(title.isBlank()||result.stream().anyMatch(x->x.title().equals(title)))continue;result.add(new OutlineItem(title,PptDocumentParser.shorten(n.path("description").asText(),80),Math.max(1,Math.min(8,n.path("slides").asInt(2)))));}
        return result;
    }
    private String outputText(JsonNode root){
        if(root==null)return""; if(root.hasNonNull("output_text"))return root.path("output_text").asText(); StringBuilder out=new StringBuilder();
        root.path("output").forEach(item->item.path("content").forEach(c->{if(c.has("text"))out.append(c.path("text").asText());})); return out.toString();
    }
    private List<OutlineItem> fallback(PptDocumentParser.ParsedDocument d){
        String all=String.join(" ",d.blocks()); List<OutlineItem> out=new ArrayList<>();
        add(out,"课题概述","研究背景、目标与价值",2); add(out,"课题设计","需求、方案与系统设计",3);
        if(all.contains("实现")||all.contains("功能")||all.contains("Spring"))add(out,"课题实现","关键模块与实现成果",3);
        else add(out,"研究过程","研究方法与实施过程",3);
        if(all.contains("测试")||all.contains("验证"))add(out,"系统测试","测试方法与结果",2); else add(out,"成果分析","主要结果与分析",2);
        return out;
    }
    private void add(List<OutlineItem> list,String t,String d,int s){list.add(new OutlineItem(t,d,s));}
    private String endpoint(){return properties.baseUrl().replaceAll("/+$","")+(properties.responsesPath().startsWith("/")?properties.responsesPath():"/"+properties.responsesPath());}
    public record OutlineItem(String title,String description,int slides){}
    public record AiOutline(List<OutlineItem> items,boolean providerInvoked,String providerStatus){}
}
