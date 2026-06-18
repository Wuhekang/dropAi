package com.dropai.rewrite.modules.partResolver;

import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.nonStandardPartGenerator.NonStandardPartGenerator;
import com.dropai.rewrite.modules.standardPartSelector.StandardPartSelector;
import org.springframework.stereotype.Service;

@Service
public class PartResolver {
    private final StandardPartSelector standardPartSelector;
    private final NonStandardPartGenerator nonStandardPartGenerator;

    public PartResolver(StandardPartSelector standardPartSelector, NonStandardPartGenerator nonStandardPartGenerator) {
        this.standardPartSelector = standardPartSelector;
        this.nonStandardPartGenerator = nonStandardPartGenerator;
    }

    public DesignProject resolve(DesignProject project) {
        project = standardPartSelector.select(project);
        return nonStandardPartGenerator.generate(project);
    }
}
