package com.dropai.rewrite.service.ppt.rendering.contract.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

final class RenderingContractTestSupport {
    static final String ROOT = "ppt/rendering-contract/v1/";
    static final ObjectMapper MAPPER = new ObjectMapper();
    static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    private RenderingContractTestSupport() {
    }

    static JsonNode read(String classpathResource) {
        try (InputStream input = RenderingContractTestSupport.class.getClassLoader()
                .getResourceAsStream(classpathResource)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing test resource: " + classpathResource);
            }
            return MAPPER.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read JSON resource: " + classpathResource, exception);
        }
    }

    static JsonNode schemaNode(String fileName) {
        return read(ROOT + fileName);
    }

    static Schema schema(String fileName) {
        return REGISTRY.getSchema(schemaNode(fileName));
    }

    static List<com.networknt.schema.Error> validate(String schemaFile, JsonNode instance) {
        return schema(schemaFile).validate(instance);
    }

    static JsonNode mutableCopyOfValid(String fileName) {
        return read(ROOT + "valid/" + fileName).deepCopy();
    }
}
