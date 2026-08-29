package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;

record LoadedTheme(
        ThemeCoordinate coordinate,
        String classpathResource,
        ObjectNode sourceDocument,
        String sourceHash
) {
    LoadedTheme {
        Objects.requireNonNull(coordinate, "coordinate");
        Objects.requireNonNull(classpathResource, "classpathResource");
        Objects.requireNonNull(sourceDocument, "sourceDocument");
        Objects.requireNonNull(sourceHash, "sourceHash");
        sourceDocument = sourceDocument.deepCopy();
    }

    @Override
    public ObjectNode sourceDocument() {
        return sourceDocument.deepCopy();
    }
}
