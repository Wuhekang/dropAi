package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ThemeValidator {
    private static final String SCHEMA_RESOURCE = "ppt/rendering-contract/v1/theme.v1.schema.json";
    private static final Set<String> FORBIDDEN_CONTENT_FIELDS = Set.of(
            "pagePurpose",
            "answerQuestion",
            "sourceChapter",
            "sourceRefs",
            "contentType",
            "imageRole",
            "mandatoryAsset",
            "paperTitle"
    );

    private final Schema schema;

    public ThemeValidator() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream input = ThemeValidator.class.getClassLoader().getResourceAsStream(SCHEMA_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing theme contract schema: " + SCHEMA_RESOURCE);
            }
            JsonNode schemaDocument = mapper.readTree(input);
            SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
            this.schema = registry.getSchema(schemaDocument);
            this.schema.initializeValidators();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load theme contract schema", exception);
        }
    }

    public void validate(ObjectNode theme) {
        List<String> violations = new ArrayList<>();
        schema.validate(theme).stream()
                .map(Object::toString)
                .sorted()
                .forEach(violations::add);
        if (!violations.isEmpty()) {
            throw new ThemeValidationException(PptQualityCode.SCHEMA_INVALID, violations);
        }

        validateInheritancePolicy(theme, violations);
        validateTypography(theme, violations);
        validateTokenReferences(theme, violations);
        validateSafeArea(theme, violations);
        scanForbiddenFields(theme, "$", violations);
        if (!violations.isEmpty()) {
            throw new ThemeValidationException(PptQualityCode.SCHEMA_INVALID, violations);
        }
    }

    private void validateInheritancePolicy(ObjectNode theme, List<String> violations) {
        String themeId = theme.path("themeId").asText();
        JsonNode inherits = theme.path("inherits");
        if (ThemeRegistry.ACADEMIC_BASE.equals(themeId) && !inherits.isEmpty()) {
            violations.add("academic-base must not inherit another theme");
        }
        if (ThemeRegistry.ACADEMIC_PURPLE.equals(themeId)) {
            String requiredParent = ThemeRegistry.ACADEMIC_BASE + "@" + ThemeRegistry.VERSION_1_0_0;
            if (inherits.size() != 1 || !requiredParent.equals(inherits.get(0).asText())) {
                violations.add("academic-purple must inherit exactly " + requiredParent);
            }
        }
    }

    private void validateTypography(ObjectNode theme, List<String> violations) {
        Iterator<Map.Entry<String, JsonNode>> styles = theme.path("typography").path("styles").fields();
        while (styles.hasNext()) {
            Map.Entry<String, JsonNode> entry = styles.next();
            double size = entry.getValue().path("sizePt").asDouble();
            double minimum = entry.getValue().path("minSizePt").asDouble();
            if (minimum > size) {
                violations.add("typography.styles." + entry.getKey() + ".minSizePt must be <= sizePt");
            }
        }

        for (String role : List.of("display", "body")) {
            Set<String> normalized = new HashSet<>();
            for (JsonNode familyNode : theme.path("typography").path("fontFamilies").path(role)) {
                String family = familyNode.asText();
                if (family.isBlank() || !family.equals(family.trim())) {
                    violations.add("typography.fontFamilies." + role + " contains a blank or untrimmed family");
                }
                if (!normalized.add(family.toLowerCase(Locale.ROOT))) {
                    violations.add("typography.fontFamilies." + role + " contains a duplicate family: " + family);
                }
            }
        }
    }

    private void validateTokenReferences(ObjectNode theme, List<String> violations) {
        Iterator<Map.Entry<String, JsonNode>> components = theme.path("components").fields();
        while (components.hasNext()) {
            Map.Entry<String, JsonNode> component = components.next();
            Iterator<Map.Entry<String, JsonNode>> tokens = component.getValue().fields();
            while (tokens.hasNext()) {
                Map.Entry<String, JsonNode> token = tokens.next();
                String reference = token.getValue().asText();
                if (resolvePath(theme, reference).isMissingNode()) {
                    violations.add("components." + component.getKey() + "." + token.getKey()
                            + " references unknown token " + reference);
                }
            }
        }
    }

    private void validateSafeArea(ObjectNode theme, List<String> violations) {
        JsonNode slide = theme.path("slide");
        JsonNode safeArea = slide.path("safeArea");
        double horizontal = safeArea.path("leftIn").asDouble() + safeArea.path("rightIn").asDouble();
        double vertical = safeArea.path("topIn").asDouble() + safeArea.path("bottomIn").asDouble();
        if (horizontal >= slide.path("widthIn").asDouble()) {
            violations.add("slide.safeArea horizontal margins must be smaller than slide width");
        }
        if (vertical >= slide.path("heightIn").asDouble()) {
            violations.add("slide.safeArea vertical margins must be smaller than slide height");
        }
    }

    private JsonNode resolvePath(JsonNode root, String dottedPath) {
        JsonNode current = root;
        for (String segment : dottedPath.split("\\.")) {
            current = current.path(segment);
        }
        return current;
    }

    private void scanForbiddenFields(JsonNode node, String path, List<String> violations) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String childPath = path + "." + field.getKey();
                if (FORBIDDEN_CONTENT_FIELDS.contains(field.getKey())) {
                    violations.add("Theme contains forbidden content field at " + childPath);
                }
                scanForbiddenFields(field.getValue(), childPath, violations);
            }
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                scanForbiddenFields(node.get(index), path + "[" + index + "]", violations);
            }
        }
    }
}
