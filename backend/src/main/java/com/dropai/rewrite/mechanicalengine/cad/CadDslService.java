package com.dropai.rewrite.mechanicalengine.cad;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import com.dropai.rewrite.mechanicalengine.cadcore.FeatureBasedCadSpec;
import com.dropai.rewrite.mechanicalengine.cadcore.FeatureInterpreter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class CadDslService {
    private final ObjectMapper mapper;
    private final FeatureInterpreter interpreter;
    public CadDslService(ObjectMapper mapper, FeatureInterpreter interpreter) {
        this.mapper = mapper;
        this.interpreter = interpreter;
    }

    public Path write(MechanicalProject project, Path workspace) {
        try {
            Path file = workspace.resolve("feature-based-cad-spec.json");
            FeatureBasedCadSpec spec = FeatureBasedCadSpec.from(project);
            interpreter.validate(spec);
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), spec);
            return file;
        } catch (Exception exception) {
            throw new IllegalStateException("CAD_DSL_WRITE_FAILED: " + exception.getMessage(), exception);
        }
    }
}
