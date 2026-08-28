package com.dropai.rewrite.service.ppt.rendering.contract.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.HashSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderingSchemaValidationTest {
    private static final List<String> SCHEMAS = List.of(
            "theme.v1.schema.json",
            "layout-recipe.v1.schema.json",
            "render-plan.v1.schema.json",
            "quality-report.v1.schema.json"
    );

    @Test
    void schemasDeclareAndPassDraft202012MetaSchema() {
        Schema metaSchema = RenderingContractTestSupport.REGISTRY.getSchema(
                SchemaLocation.of("https://json-schema.org/draft/2020-12/schema"));

        for (String schemaFile : SCHEMAS) {
            JsonNode schemaNode = RenderingContractTestSupport.schemaNode(schemaFile);
            assertEquals("https://json-schema.org/draft/2020-12/schema", schemaNode.path("$schema").asText());
            assertTrue(metaSchema.validate(schemaNode).isEmpty(),
                    () -> schemaFile + " is not a valid Draft 2020-12 schema: " + metaSchema.validate(schemaNode));
            RenderingContractTestSupport.schema(schemaFile).initializeValidators();
        }
    }

    @Test
    void schemaIdsAreUniqueAndStable() {
        var ids = new HashSet<String>();
        for (String schemaFile : SCHEMAS) {
            String id = RenderingContractTestSupport.schemaNode(schemaFile).path("$id").asText();
            assertFalse(id.isBlank(), () -> schemaFile + " must declare $id");
            assertTrue(ids.add(id), () -> "Duplicate schema $id: " + id);
        }
    }

    @ParameterizedTest(name = "valid example {1}")
    @MethodSource("validExamples")
    void validExamplesPass(String schemaFile, String exampleFile) {
        JsonNode example = RenderingContractTestSupport.read(RenderingContractTestSupport.ROOT + "valid/" + exampleFile);
        assertTrue(RenderingContractTestSupport.validate(schemaFile, example).isEmpty(),
                () -> exampleFile + " must be valid: " + RenderingContractTestSupport.validate(schemaFile, example));
    }

    @ParameterizedTest(name = "invalid example {1}")
    @MethodSource("invalidExamples")
    void invalidExamplesFail(String schemaFile, String exampleFile) {
        JsonNode example = RenderingContractTestSupport.read(RenderingContractTestSupport.ROOT + "invalid/" + exampleFile);
        assertFalse(RenderingContractTestSupport.validate(schemaFile, example).isEmpty(),
                () -> exampleFile + " must be rejected");
    }

    private static Stream<Arguments> validExamples() {
        return Stream.of(
                Arguments.of("theme.v1.schema.json", "theme.valid.json"),
                Arguments.of("layout-recipe.v1.schema.json", "layout-recipe.valid.json"),
                Arguments.of("render-plan.v1.schema.json", "render-plan.valid.json"),
                Arguments.of("quality-report.v1.schema.json", "quality-report.valid.json")
        );
    }

    private static Stream<Arguments> invalidExamples() {
        return Stream.of(
                Arguments.of("theme.v1.schema.json", "theme.unknown-property.json"),
                Arguments.of("layout-recipe.v1.schema.json", "layout-recipe.unknown-enum.json"),
                Arguments.of("render-plan.v1.schema.json", "render-plan.invalid-geometry.json"),
                Arguments.of("render-plan.v1.schema.json", "render-plan.invalid-element.json"),
                Arguments.of("quality-report.v1.schema.json", "quality-report.invalid-code.json")
        );
    }
}
