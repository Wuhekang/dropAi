package com.dropai.rewrite.service.ppt.rendering.compiler.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;

/**
 * Immutable boundary object for a presentation tree that has already passed the
 * content and outline validation stages.  Commit 5 deliberately does not
 * reinterpret or repair this document.
 */
public final class ValidatedPresentationTree {
    private final ObjectNode document;
    private final String presentationId;
    private final String sourceTreeHash;

    public ValidatedPresentationTree(
            ObjectNode document,
            String presentationId,
            String sourceTreeHash
    ) {
        Objects.requireNonNull(document, "document");
        this.document = document.deepCopy();
        this.presentationId = requirePortableId(presentationId, "presentationId");
        this.sourceTreeHash = requireSha256(sourceTreeHash);
        requireArray(this.document, "pages");
        requireObject(this.document, "metadata");
    }

    public ObjectNode document() {
        return document.deepCopy();
    }

    public JsonNode pages() {
        return document.path("pages").deepCopy();
    }

    public JsonNode metadata() {
        return document.path("metadata").deepCopy();
    }

    public JsonNode agendaSections() {
        return document.path("agendaSections").deepCopy();
    }

    public String presentationId() {
        return presentationId;
    }

    public String sourceTreeHash() {
        return sourceTreeHash;
    }

    private static void requireArray(ObjectNode owner, String field) {
        if (!owner.path(field).isArray() || owner.path(field).isEmpty()) {
            throw new IllegalArgumentException(field + " must be a non-empty array");
        }
    }

    private static void requireObject(ObjectNode owner, String field) {
        if (!owner.path(field).isObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
    }

    private static String requiredText(ObjectNode owner, String field) {
        JsonNode value = owner.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return value.textValue();
    }

    private static String requireSha256(String value) {
        Objects.requireNonNull(value, "sourceTreeHash");
        if (!value.matches("^sha256:[a-f0-9]{64}$")) {
            throw new IllegalArgumentException("sourceTreeHash must be a prefixed lowercase SHA-256");
        }
        return value;
    }

    private static String requirePortableId(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!value.matches("^[A-Za-z0-9][A-Za-z0-9._-]*$")) {
            throw new IllegalArgumentException(field + " must be a portable identifier");
        }
        return value;
    }
}
