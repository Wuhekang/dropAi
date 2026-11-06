package com.dropai.rewrite.service.ppt.rendering.production.v1;

import com.dropai.rewrite.service.ppt.rendering.bundle.v1.ProductionFontFace;
import com.dropai.rewrite.service.ppt.rendering.bundle.v1.ProductionFontInventory;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.FontFaceInventory;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.FontFaceResource;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.FontSource;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.ResolvedFontProfile;

import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Loads explicitly configured font files; it never asks the OS for an implicit fallback. */
final class ProductionFontInventoryLoader {
    static final String FONT_FILES_PROPERTY = "dokiai.ppt.font.files";

    LoadedFonts load() {
        String configured = System.getProperty(FONT_FILES_PROPERTY,
                System.getenv().getOrDefault("DOKIAI_PPT_FONT_FILES", ""));
        if (configured.isBlank()) {
            throw new IllegalStateException("PPT Rendering V1 requires explicit font files via "
                    + FONT_FILES_PROPERTY + " or DOKIAI_PPT_FONT_FILES");
        }
        List<FontFaceResource> resources = new ArrayList<>();
        Set<Integer> declaredWeights = new LinkedHashSet<>();
        for (String token : configured.split(",")) {
            String[] declaration=token.trim().split("=",2);
            if(declaration.length!=2||!declaration[0].matches("[1-9]00")){
                throw new IllegalStateException("Each configured PPT font must use weight=/absolute/path syntax");
            }
            int weight=Integer.parseInt(declaration[0]);
            if(!declaredWeights.add(weight))throw new IllegalStateException("Duplicate configured PPT font weight: "+weight);
            Path configuredPath=Path.of(declaration[1]);
            if(!configuredPath.isAbsolute())throw new IllegalStateException("Configured PPT font path must be absolute: "+configuredPath);
            Path path = configuredPath.normalize();
            try {
                byte[] bytes = Files.readAllBytes(path);
                Font[] faces = Font.createFonts(new ByteArrayInputStream(bytes));
                for (Font face : faces) {
                    boolean expectedBold = weight >= 600;
                    if (intrinsicBold(face) != expectedBold) {
                        throw new IllegalStateException("Configured PPT font style does not match weight "
                                + weight + ": " + path + " (font=" + face.getFontName(Locale.ENGLISH) + ")");
                    }
                    resources.add(new FontFaceResource(face.getFamily(Locale.ENGLISH), face.getPSName(), weight,
                            FontSource.PROVIDED, bytes));
                }
            } catch (IllegalStateException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot load configured PPT font file: " + path, exception);
            }
        }
        Set<Integer> requiredWeights=Set.of(400,500,600,700);
        if(!declaredWeights.equals(requiredWeights)){
            throw new IllegalStateException("PPT Rendering V1 requires explicit font weights "+requiredWeights
                    +" but found "+declaredWeights);
        }
        FontFaceInventory inventory = (family, weight) -> resources.stream()
                .filter(face -> face.family().equalsIgnoreCase(family) && face.weight() == weight)
                .toList();
        return new LoadedFonts(inventory, resources);
    }

    private static boolean intrinsicBold(Font face) {
        String identity = (face.getFontName(Locale.ENGLISH) + " " + face.getPSName())
                .toLowerCase(Locale.ROOT);
        return face.isBold()
                || identity.contains("bold")
                || identity.contains("demi")
                || identity.contains("black");
    }

    record LoadedFonts(FontFaceInventory measurementInventory, List<FontFaceResource> resources) {
        ProductionFontInventory production(ResolvedFontProfile resolved) {
            List<ProductionFontFace> faces = new ArrayList<>();
            resolved.faces().forEach((role, weights) -> weights.forEach((weight, face) ->
                    faces.add(new ProductionFontFace(role + "-" + weight, role, weight,
                            resolved.requestedFamilies().get(role).get(0), face.selectedFamily(),
                            face.postScriptName(), face.fontSource().name(), face.fontFingerprint(),
                            face.fallbackApplied()))));
            return new ProductionFontInventory(resolved.profileId(), resolved.fontProfileHash(),
                    resolved.measurementEngineVersion(), faces);
        }
    }
}
