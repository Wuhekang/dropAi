package com.dropai.rewrite.modules.parameterEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

@Service
public class ParameterEngine {
    public DesignProject normalize(DesignProject project) {
        return project == null ? new DesignProject() : project;
    }
}
