package com.dropai.rewrite.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import java.nio.file.*;
import java.util.*;

/** Optional local desktop credentials; OS environment and command-line options still win. */
public class MechanicalPaperDesktopEnvironment implements EnvironmentPostProcessor {
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path desktop = Path.of(System.getProperty("user.home"), "Desktop", ".env");
        if (!Files.isRegularFile(desktop)) desktop = Path.of(System.getProperty("user.home"), "桌面", ".env");
        if (!Files.isRegularFile(desktop)) return;
        try {
            Map<String,Object> values = new LinkedHashMap<>();
            for (String line : Files.readAllLines(desktop)) {
                String cleaned = line.replace("\uFEFF", "").trim();
                int eq = cleaned.indexOf('=');
                if (eq < 1 || cleaned.startsWith("#")) continue;
                String key = cleaned.substring(0, eq).trim(), value = cleaned.substring(eq + 1).trim();
                if (!key.startsWith("DOUBAO_")) continue;
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) value = value.substring(1, value.length()-1);
                values.put(key, value);
            }
            environment.getPropertySources().addLast(new MapPropertySource("mechanicalPaperDesktopEnv", values));
        } catch (Exception ignored) { /* Never print credential file contents. */ }
    }
}
