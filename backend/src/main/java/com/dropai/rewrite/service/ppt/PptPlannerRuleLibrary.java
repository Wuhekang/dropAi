package com.dropai.rewrite.service.ppt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class PptPlannerRuleLibrary {
    private final ObjectMapper mapper;

    public PptPlannerRuleLibrary(ObjectMapper mapper){this.mapper=mapper;}

    public RuleSet load(String majorType){
        RuleSet general=read("general");String normalized=majorType==null?"general":majorType.trim().toLowerCase();
        if(normalized.isBlank()||"general".equals(normalized))return general;
        RuleSet specialized=read(resourceExists(normalized)?normalized:"general");
        if("general".equals(specialized.majorType()))return general;
        return new RuleSet(specialized.majorType(),merge(general.metadataLabels(),specialized.metadataLabels()),merge(general.formMarkers(),specialized.formMarkers()),merge(general.forbiddenStandalone(),specialized.forbiddenStandalone()),merge(general.technologyTerms(),specialized.technologyTerms()),mergeRules(general.purposeRules(),specialized.purposeRules()));
    }

    private RuleSet read(String name){try(InputStream in=new ClassPathResource("knowledge/ppt/"+name+".json").getInputStream()){return mapper.readValue(in,RuleSet.class);}catch(Exception e){throw new IllegalStateException("无法读取PPT专业规则: "+name,e);}}
    private boolean resourceExists(String name){return new ClassPathResource("knowledge/ppt/"+name+".json").exists();}
    private List<String> merge(List<String>a,List<String>b){return new ArrayList<>(new LinkedHashSet<>(concat(a,b)));}
    private List<PurposeRule> mergeRules(List<PurposeRule>a,List<PurposeRule>b){List<PurposeRule> out=new ArrayList<>();if(b!=null)out.addAll(b);if(a!=null)for(PurposeRule rule:a)if(out.stream().noneMatch(x->x.purpose().equals(rule.purpose())))out.add(rule);return out;}
    private <T> List<T> concat(List<T>a,List<T>b){List<T> out=new ArrayList<>();if(a!=null)out.addAll(a);if(b!=null)out.addAll(b);return out;}

    public record RuleSet(String majorType,List<String> metadataLabels,List<String> formMarkers,List<String> forbiddenStandalone,List<String> technologyTerms,List<PurposeRule> purposeRules){}
    public record PurposeRule(String purpose,List<String> keywords,String title,String answerQuestion){}
}
