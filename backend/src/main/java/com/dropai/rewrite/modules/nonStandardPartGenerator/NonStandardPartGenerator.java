package com.dropai.rewrite.modules.nonStandardPartGenerator;

import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.unknownPartResolver.UnknownPartResolver;
import org.springframework.stereotype.Service;

@Service
public class NonStandardPartGenerator {
    private final UnknownPartResolver delegate;

    public NonStandardPartGenerator(UnknownPartResolver delegate) {
        this.delegate = delegate;
    }

    public DesignProject generate(DesignProject project) {
        DesignProject resolved = delegate.resolve(project);
        resolved.getResolvedParts().forEach(part -> {
            if ("non_standard".equals(part.getPartType())) {
                part.setGeneratedBy("NonStandardPartGenerator");
                part.setSource("StructureTree + generated geometry features");
            }
        });
        return resolved;
    }
}
