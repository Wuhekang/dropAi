package com.dropai.generator;

import com.dropai.generator.blueprint.BlueprintPipeline;
import com.dropai.generator.engine.TemplateEngine;
import java.nio.file.*;

public final class GeneratorMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: GeneratorMain <blueprint.json> <output-directory>");
        BlueprintPipeline pipeline = new BlueprintPipeline();
        var blueprint = pipeline.parse(Files.readAllBytes(Path.of(args[0])));
        var model = pipeline.normalize(blueprint);
        Files.createDirectories(Path.of(args[1]));
        try (TemplateEngine engine = new TemplateEngine()) {
            System.out.println(engine.generate(model, Path.of(args[1])));
        }
    }
}
