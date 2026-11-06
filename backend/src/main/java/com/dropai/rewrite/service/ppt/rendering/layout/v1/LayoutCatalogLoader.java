package com.dropai.rewrite.service.ppt.rendering.layout.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class LayoutCatalogLoader {
    public static final String CATALOG_RESOURCE = "ppt/layouts/v1/layout-catalog.json";
    private static final Set<String> CATALOG_FIELDS = Set.of("catalogId", "catalogVersion", "layouts");
    private static final Set<String> ENTRY_FIELDS = Set.of("layoutId", "resource");

    private final ObjectMapper mapper;
    private final LayoutCatalogValidator validator;
    private final LayoutCanonicalizer canonicalizer;
    private final LayoutHasher hasher;

    public LayoutCatalogLoader() {
        this(new LayoutCatalogValidator(), new LayoutCanonicalizer());
    }

    public LayoutCatalogLoader(LayoutCatalogValidator validator, LayoutCanonicalizer canonicalizer) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
        this.hasher = new LayoutHasher(canonicalizer);
        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        this.mapper = new ObjectMapper(factory);
    }

    public LayoutCatalog loadAcademicV1() {
        ObjectNode manifest = readObject(CATALOG_RESOURCE);
        validateManifest(manifest);

        List<LayoutRecipe> recipes = new ArrayList<>();
        ArrayNode resolvedRecipes = JsonNodeFactory.instance.arrayNode();
        for (JsonNode entry : manifest.path("layouts")) {
            String expectedId = entry.path("layoutId").asText();
            String resource = entry.path("resource").asText();
            ObjectNode source = readObject(resource);
            validator.validateSource(source);
            if (!expectedId.equals(source.path("layoutId").asText())) {
                throw new LayoutValidationException(
                        PptQualityCode.INVALID_REFERENCE,
                        "Catalog entry " + expectedId + " points to recipe " + source.path("layoutId").asText());
            }
            try {
                recipes.add(mapper.treeToValue(source, LayoutRecipe.class));
            } catch (IOException | IllegalArgumentException exception) {
                throw new LayoutValidationException(
                        PptQualityCode.SCHEMA_INVALID,
                        "Unable to map layout recipe " + resource + ": " + firstLine(exception.getMessage()));
            }
            resolvedRecipes.add(source.deepCopy());
        }
        validator.validateCatalog(recipes);

        ObjectNode resolvedCatalog = JsonNodeFactory.instance.objectNode();
        resolvedCatalog.put("catalogId", manifest.path("catalogId").asText());
        resolvedCatalog.put("catalogVersion", manifest.path("catalogVersion").asText());
        resolvedCatalog.set("layouts", resolvedRecipes);
        String canonicalDocument = canonicalizer.canonicalize(resolvedCatalog);
        return new LayoutCatalog(
                manifest.path("catalogId").asText(),
                manifest.path("catalogVersion").asText(),
                recipes,
                canonicalDocument,
                hasher.hashCanonical(canonicalDocument));
    }

    private ObjectNode readObject(String classpathResource) {
        try (InputStream input = LayoutCatalogLoader.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (input == null) {
                throw new LayoutValidationException(
                        PptQualityCode.INVALID_REFERENCE,
                        "Missing layout resource: " + classpathResource);
            }
            String json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(input.readAllBytes()))
                    .toString();
            JsonNode node = mapper.readTree(json);
            if (!(node instanceof ObjectNode object)) {
                throw new LayoutValidationException(
                        PptQualityCode.SCHEMA_INVALID,
                        "Layout resource root must be an object: " + classpathResource);
            }
            return object;
        } catch (LayoutValidationException exception) {
            throw exception;
        } catch (IOException exception) {
            PptQualityCode code = exception.getMessage() != null && exception.getMessage().contains("Duplicate field")
                    ? PptQualityCode.DUPLICATE_ID
                    : PptQualityCode.SCHEMA_INVALID;
            throw new LayoutValidationException(
                    code,
                    "Invalid layout JSON " + classpathResource + ": " + firstLine(exception.getMessage()));
        }
    }

    private void validateManifest(ObjectNode manifest) {
        List<String> violations = new ArrayList<>();
        Set<String> actualFields = new HashSet<>();
        manifest.fieldNames().forEachRemaining(actualFields::add);
        if (!actualFields.equals(CATALOG_FIELDS)) {
            violations.add("layout-catalog.json must contain exactly " + CATALOG_FIELDS);
        }
        if (!manifest.path("catalogId").isTextual() || manifest.path("catalogId").asText().isBlank()) {
            violations.add("catalogId must be a non-blank string");
        }
        if (!manifest.path("catalogVersion").asText().matches("^[0-9]+\\.[0-9]+\\.[0-9]+$")) {
            violations.add("catalogVersion must be semantic version text");
        }
        JsonNode entries = manifest.path("layouts");
        if (!entries.isArray() || entries.isEmpty()) {
            violations.add("layouts must be a non-empty array");
        } else {
            Set<String> ids = new HashSet<>();
            Set<String> resources = new HashSet<>();
            for (int index = 0; index < entries.size(); index++) {
                JsonNode entry = entries.get(index);
                Set<String> fields = new HashSet<>();
                entry.fieldNames().forEachRemaining(fields::add);
                if (!entry.isObject() || !fields.equals(ENTRY_FIELDS)) {
                    violations.add("layouts[" + index + "] must contain exactly " + ENTRY_FIELDS);
                    continue;
                }
                String id = entry.path("layoutId").asText();
                String resource = entry.path("resource").asText();
                if (!ids.add(id)) {
                    violations.add("Duplicate catalog layoutId " + id);
                }
                if (!resources.add(resource)) {
                    violations.add("Duplicate catalog resource " + resource);
                }
                if (!resource.matches("^ppt/layouts/v1/[a-z0-9.-]+\\.json$")) {
                    violations.add("Invalid relative layout resource " + resource);
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new LayoutValidationException(PptQualityCode.SCHEMA_INVALID, violations.stream().sorted().toList());
        }
    }

    private String firstLine(String value) {
        if (value == null || value.isBlank()) {
            return "unknown parsing error";
        }
        int lineBreak = value.indexOf('\n');
        return lineBreak < 0 ? value : value.substring(0, lineBreak);
    }
}
