package com.dropai.rewrite.mechanicalengine.cad;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class CadDslService {
    private final ObjectMapper mapper;
    public CadDslService(ObjectMapper mapper) { this.mapper = mapper; }

    public Path write(MechanicalProject project, Path workspace) {
        try {
            Path file = workspace.resolve("cad-model-spec.json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), project);
            return file;
        } catch (Exception exception) {
            throw new IllegalStateException("CAD_DSL_WRITE_FAILED: " + exception.getMessage(), exception);
        }
    }
}
