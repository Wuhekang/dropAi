package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import java.util.Collection;
import java.util.Objects;

public final class ThemeEngine {
    private final ThemeRegistry registry;
    private final ThemeResolver resolver;

    private ThemeEngine(ThemeRegistry registry, ThemeResolver resolver) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public static ThemeEngine academicV1(Collection<String> availableFontFamilies) {
        ThemeCanonicalizer canonicalizer = new ThemeCanonicalizer();
        ThemeHasher hasher = new ThemeHasher(canonicalizer);
        ThemeRegistry registry = ThemeRegistry.academicV1();
        ThemeLoader loader = new ThemeLoader(hasher);
        ThemeValidator validator = new ThemeValidator();
        FontAvailabilityChecker fontChecker = new FontAvailabilityChecker(availableFontFamilies, hasher);
        ColorContrastChecker contrastChecker = new ColorContrastChecker();
        ThemeResolver resolver = new ThemeResolver(
                registry,
                loader,
                validator,
                fontChecker,
                contrastChecker,
                canonicalizer,
                hasher);
        return new ThemeEngine(registry, resolver);
    }

    public static ThemeEngine systemAcademicV1() {
        ThemeCanonicalizer canonicalizer = new ThemeCanonicalizer();
        ThemeHasher hasher = new ThemeHasher(canonicalizer);
        ThemeRegistry registry = ThemeRegistry.academicV1();
        ThemeLoader loader = new ThemeLoader(hasher);
        ThemeValidator validator = new ThemeValidator();
        FontAvailabilityChecker fontChecker = FontAvailabilityChecker.system(hasher);
        ColorContrastChecker contrastChecker = new ColorContrastChecker();
        ThemeResolver resolver = new ThemeResolver(
                registry,
                loader,
                validator,
                fontChecker,
                contrastChecker,
                canonicalizer,
                hasher);
        return new ThemeEngine(registry, resolver);
    }

    public ResolvedTheme resolve(ThemeResolutionRequest request) {
        return resolver.resolve(request);
    }

    public ThemeRegistry registry() {
        return registry;
    }
}
