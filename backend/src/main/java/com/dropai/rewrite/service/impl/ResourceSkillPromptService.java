package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.service.SkillPromptService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class ResourceSkillPromptService implements SkillPromptService {

    @Override
    public String loadSkill(String skillName) {
        ClassPathResource resource = new ClassPathResource("skills/" + skillName + "/SKILL.md");
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Skill 文件读取失败：" + skillName, exception);
        }
    }
}
