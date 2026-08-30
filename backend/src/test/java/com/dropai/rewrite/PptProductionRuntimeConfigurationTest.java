package com.dropai.rewrite;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PptProductionRuntimeConfigurationTest {
    @Test
    void dockerRuntimePinsExplicitCjkFontsAndTheDeployedGitCommit() throws Exception {
        Path repository=Path.of("..").toAbsolutePath().normalize();
        String composeText=Files.readString(repository.resolve("docker-compose.yml"));
        Map<String,Object> compose=new Yaml().load(composeText);
        Map<String,Object> services=map(compose.get("services"));
        Map<String,Object> backend=map(services.get("backend"));
        Map<String,Object> build=map(backend.get("build"));
        Map<String,Object> args=map(build.get("args"));
        Map<String,Object> environment=map(backend.get("environment"));

        assertTrue(String.valueOf(args.get("DOKIAI_GIT_COMMIT")).contains(":?"));
        assertTrue(String.valueOf(environment.get("DOKIAI_GIT_COMMIT")).contains(":?"));
        String fonts=String.valueOf(environment.get("DOKIAI_PPT_FONT_FILES"));
        for(String weight:new String[]{"400=","500=","600=","700="})assertTrue(fonts.contains(weight));

        String dockerfile=Files.readString(repository.resolve("backend/Dockerfile"));
        assertTrue(dockerfile.contains("fonts-noto-cjk"));
        assertTrue(dockerfile.contains("NotoSansCJK-Regular.ttc"));
        assertTrue(dockerfile.contains("NotoSansCJK-Bold.ttc"));
        assertTrue(dockerfile.contains("DOKIAI_GIT_COMMIT"));
        assertTrue(dockerfile.contains("[a-f0-9]{40}"));

        String example=Files.readString(repository.resolve(".env.example"));
        assertTrue(example.contains("DOKIAI_PPT_FONT_FILES="));
        assertTrue(example.contains("DOKIAI_GIT_COMMIT="));
        assertEquals(1,example.lines().filter(line->line.startsWith("DOKIAI_GIT_COMMIT=")).count());
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> map(Object value) {
        return (Map<String,Object>)value;
    }
}
