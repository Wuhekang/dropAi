package com.dropai.rewrite.modules.partGeneratorAgent;

import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.partResolver.PartResolver;
import org.springframework.stereotype.Service;

@Service
public class PartGeneratorAgent {
    public DesignProject generate(DesignProject project, PartResolver partResolver) {
        DesignProject resolved = partResolver.resolve(project);
        resolved.getEnhancementNotes().removeIf(note -> note != null && note.contains("PartGeneratorAgent"));
        resolved.getEnhancementNotes().add("PartGeneratorAgent：已按结构树叶子零件判断标准件/非标件，标准件进入StandardPartSelector，未知件进入NonStandardPartGenerator。");
        return resolved;
    }
}
