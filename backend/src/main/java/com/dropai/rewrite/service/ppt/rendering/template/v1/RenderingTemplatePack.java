package com.dropai.rewrite.service.ppt.rendering.template.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PageType;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.AssetBinaryResolver;
import com.dropai.rewrite.service.ppt.rendering.theme.v1.ThemeResolutionRequest;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, security-reviewed visual assets for a selectable production template.
 * It contains no source presentation package and therefore cannot carry OLE, links,
 * comments, macros, audio, embedded fonts or sample text into the generated PPTX.
 */
public final class RenderingTemplatePack {
    private final String templatePackId;
    private final String templatePackVersion;
    private final String displayName;
    private final String description;
    private final ThemeResolutionRequest themeRequest;
    private final String sourceTemplateHash;
    private final String templatePackHash;
    private final ArrayNode assets;
    private final Map<PageType, String> surfaceAssetIds;
    private final AssetBinaryResolver assetResolver;

    RenderingTemplatePack(
            String templatePackId,
            String templatePackVersion,
            String displayName,
            String description,
            ThemeResolutionRequest themeRequest,
            String sourceTemplateHash,
            String templatePackHash,
            ArrayNode assets,
            Map<PageType, String> surfaceAssetIds,
            AssetBinaryResolver assetResolver
    ) {
        this.templatePackId = required(templatePackId, "templatePackId");
        this.templatePackVersion = required(templatePackVersion, "templatePackVersion");
        this.displayName = required(displayName, "displayName");
        this.description = required(description, "description");
        this.themeRequest = Objects.requireNonNull(themeRequest, "themeRequest");
        this.sourceTemplateHash = required(sourceTemplateHash, "sourceTemplateHash");
        this.templatePackHash = required(templatePackHash, "templatePackHash");
        this.assets = Objects.requireNonNull(assets, "assets").deepCopy();
        EnumMap<PageType, String> surfaces = new EnumMap<>(PageType.class);
        Objects.requireNonNull(surfaceAssetIds, "surfaceAssetIds")
                .forEach((type, assetId) -> surfaces.put(
                        Objects.requireNonNull(type, "surface page type"),
                        required(assetId, "surface assetId")));
        this.surfaceAssetIds = Collections.unmodifiableMap(surfaces);
        this.assetResolver = Objects.requireNonNull(assetResolver, "assetResolver");
    }

    public String templatePackId() { return templatePackId; }
    public String templatePackVersion() { return templatePackVersion; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public ThemeResolutionRequest themeRequest() { return themeRequest; }
    public String sourceTemplateHash() { return sourceTemplateHash; }
    public String templatePackHash() { return templatePackHash; }
    public ArrayNode assets() { return assets.deepCopy(); }
    public AssetBinaryResolver assetResolver() { return assetResolver; }
    public boolean decorated() { return !surfaceAssetIds.isEmpty(); }
    public Optional<String> surfaceAssetId(PageType pageType) {
        return Optional.ofNullable(surfaceAssetIds.get(pageType));
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
