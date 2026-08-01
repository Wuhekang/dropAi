package com.dropai.rewrite.modules.pluginDiscoveryAgent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Reports capability gaps. Installation is deliberately an operator-approved action. */
public class PluginDiscoveryAgent {
    private static final Set<String> REGISTERED = Set.of(
            "STEP_EXPORT", "STEP_REOPEN_VALIDATION", "DXF_GENERATION", "SVG_RENDER",
            "PNG_RENDER", "ASSEMBLY_CONSTRAINT_AUDIT", "BOM_NORMALIZATION");

    public DiscoveryReport inspect(Set<String> requiredCapabilities) {
        List<String> missing = new ArrayList<>();
        for (String capability : requiredCapabilities) {
            if (!REGISTERED.contains(capability)) missing.add(capability);
        }
        return new DiscoveryReport(missing.isEmpty(), List.copyOf(REGISTERED), missing,
                missing.isEmpty() ? "all required capabilities registered"
                        : "operator approval required before installing or registering providers");
    }

    public record DiscoveryReport(boolean ready, List<String> registered, List<String> missing, String action) {}
}
