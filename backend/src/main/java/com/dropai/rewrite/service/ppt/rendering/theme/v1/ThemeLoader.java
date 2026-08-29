package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class ThemeLoader {
    private final ObjectMapper mapper;
    private final ThemeHasher hasher;

    public ThemeLoader(ThemeHasher hasher) {
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        this.mapper = new ObjectMapper(factory);
    }

    LoadedTheme load(ThemeRegistry.Registration registration) {
        Objects.requireNonNull(registration, "registration");
        try (InputStream input = ThemeLoader.class.getClassLoader()
                .getResourceAsStream(registration.classpathResource())) {
            if (input == null) {
                throw new ThemeValidationException(
                        PptQualityCode.INVALID_REFERENCE,
                        "Missing theme resource: " + registration.classpathResource());
            }
            return parse(input.readAllBytes(), registration);
        } catch (ThemeValidationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ThemeValidationException(
                    PptQualityCode.SCHEMA_INVALID,
                    "Unable to read theme resource " + registration.classpathResource() + ": " + exception.getMessage());
        }
    }

    LoadedTheme parse(byte[] utf8Json, ThemeRegistry.Registration registration) {
        Objects.requireNonNull(utf8Json, "utf8Json");
        Objects.requireNonNull(registration, "registration");
        try {
            String json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(utf8Json))
                    .toString();
            JsonNode parsed = mapper.readTree(json);
            if (!(parsed instanceof ObjectNode object)) {
                throw new ThemeValidationException(
                        PptQualityCode.SCHEMA_INVALID,
                        "Theme root must be a JSON object: " + registration.classpathResource());
            }
            ThemeCoordinate declared = new ThemeCoordinate(
                    object.path("themeId").asText(),
                    object.path("themeVersion").asText());
            if (!declared.equals(registration.coordinate())) {
                throw new ThemeValidationException(
                        PptQualityCode.INVALID_REFERENCE,
                        "Theme registration " + registration.coordinate().reference()
                                + " does not match document " + declared.reference());
            }
            return new LoadedTheme(
                    declared,
                    registration.classpathResource(),
                    object,
                    hasher.hash(object));
        } catch (ThemeValidationException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ThemeValidationException(
                    PptQualityCode.SCHEMA_INVALID,
                    "Invalid theme identity in " + registration.classpathResource() + ": " + exception.getMessage());
        } catch (IOException exception) {
            PptQualityCode code = exception.getMessage() != null
                    && exception.getMessage().contains("Duplicate field")
                    ? PptQualityCode.DUPLICATE_ID
                    : PptQualityCode.SCHEMA_INVALID;
            throw new ThemeValidationException(
                    code,
                    "Invalid theme JSON in " + registration.classpathResource() + ": " + firstLine(exception.getMessage()));
        }
    }

    private String firstLine(String value) {
        if (value == null || value.isBlank()) {
            return "unknown JSON parsing error";
        }
        int lineBreak = value.indexOf('\n');
        return lineBreak < 0 ? value : value.substring(0, lineBreak);
    }
}
