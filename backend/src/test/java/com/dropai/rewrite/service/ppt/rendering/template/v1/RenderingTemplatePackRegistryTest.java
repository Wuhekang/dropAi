package com.dropai.rewrite.service.ppt.rendering.template.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PageType;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.VerifiedAssetBytes;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderingTemplatePackRegistryTest {
    @Test
    void decoratedPackIsClasspathOnlyHashVerifiedAndCoversEveryPageType() {
        RenderingTemplatePack pack = decorated(new RenderingTemplatePackRegistry());

        assertEquals(Set.of(PageType.values()), packSurfaceTypes(pack));
        assertEquals(pack.templatePackId(), pack.themeRequest().themeId());
        assertTrue(pack.sourceTemplateHash().matches("sha256:[a-f0-9]{64}"));
        assertTrue(pack.templatePackHash().matches("sha256:[a-f0-9]{64}"));
        assertFalse(pack.assets().isEmpty());
        for (JsonNode asset : pack.assets()) {
            assertEquals("TEMPLATE_DECORATION", asset.path("assetKind").asText());
            String bundlePath = asset.path("bundlePath").asText();
            assertTrue(bundlePath.startsWith("assets/templates/"));
            assertFalse(bundlePath.endsWith(".pptx"));
            assertFalse(bundlePath.endsWith(".xlsx"));
            assertFalse(bundlePath.endsWith(".bin"));
            VerifiedAssetBytes bytes = pack.assetResolver().resolve(
                    asset.path("assetId").asText(),
                    bundlePath,
                    asset.path("sha256").asText());
            assertNotNull(bytes);
            assertTrue(bytes.bytes().length > 0);
            assertNull(pack.assetResolver().resolve(
                    asset.path("assetId").asText(), bundlePath, "sha256:" + "0".repeat(64)));
        }
    }

    @Test
    void registrationAndManifestHashAreDeterministicAndUnknownIdsFailClosed() {
        RenderingTemplatePackRegistry first = new RenderingTemplatePackRegistry();
        RenderingTemplatePackRegistry second = new RenderingTemplatePackRegistry();
        RenderingTemplatePack firstPack = decorated(first);
        RenderingTemplatePack secondPack = second.require(firstPack.templatePackId());

        assertEquals(firstPack.templatePackHash(), secondPack.templatePackHash());
        assertEquals(firstPack.assets(), secondPack.assets());
        assertThrows(IllegalArgumentException.class, () -> first.require("unknown-template-pack"));
        assertThrows(IllegalArgumentException.class, () -> first.require(" "));
    }

    @Test
    void persistedSelectionAcceptsTrustedPacksAndFiniteLegacyValuesOnly() {
        assertEquals(RenderingTemplatePackRegistry.ACADEMIC_PURPLE,
                RenderingTemplatePackRegistry.selectedPackId(Map.of()));
        assertEquals(RenderingTemplatePackRegistry.ACADEMIC_PURPLE,
                RenderingTemplatePackRegistry.selectedPackId(Map.of("template_style", "AI_RECOMMEND")));
        assertEquals(RenderingTemplatePackRegistry.ACADEMIC_PURPLE,
                RenderingTemplatePackRegistry.selectedPackId(Map.of("template_style", "TECH_DEFENSE")));
        assertEquals(RenderingTemplatePackRegistry.SMALL_BEAR_WATERCOLOR_BLUE_V1,
                RenderingTemplatePackRegistry.selectedPackId(Map.of(
                        "template_id", RenderingTemplatePackRegistry.SMALL_BEAR_WATERCOLOR_BLUE_V1,
                        "template_style", RenderingTemplatePackRegistry.SMALL_BEAR_WATERCOLOR_BLUE_V1)));

        assertThrows(IllegalArgumentException.class,
                () -> RenderingTemplatePackRegistry.selectedPackId(Map.of("template_style", "CUSTOM")));
        assertThrows(IllegalArgumentException.class,
                () -> RenderingTemplatePackRegistry.selectedPackId(Map.of("template_id", "user-uploaded-template")));
        assertThrows(IllegalArgumentException.class,
                () -> RenderingTemplatePackRegistry.selectedPackId(Map.of(
                        "template_id", RenderingTemplatePackRegistry.SMALL_BEAR_WATERCOLOR_BLUE_V1,
                        "template_style", RenderingTemplatePackRegistry.ACADEMIC_PURPLE)));
        assertThrows(IllegalArgumentException.class,
                () -> RenderingTemplatePackRegistry.normalizeSelection("damaged-template-value"));
    }

    private RenderingTemplatePack decorated(RenderingTemplatePackRegistry registry) {
        return registry.available().stream()
                .filter(RenderingTemplatePack::decorated)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected one trusted decorated template pack"));
    }

    private Set<PageType> packSurfaceTypes(RenderingTemplatePack pack) {
        java.util.EnumSet<PageType> types = java.util.EnumSet.noneOf(PageType.class);
        for (PageType type : PageType.values()) {
            if (pack.surfaceAssetId(type).isPresent()) {
                types.add(type);
            }
        }
        return Set.copyOf(types);
    }
}
