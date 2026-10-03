package com.dropai.rewrite.config;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DesktopEnvLoader {
    private DesktopEnvLoader() {
    }

    public static String get(String key) {
        String fromEnv = System.getenv(key);
        if (notBlank(fromEnv)) return fromEnv.trim();
        String userHome = System.getProperty("user.home", "");
        List<Path> candidates = List.of(
                Path.of(userHome, "Desktop", ".env"),
                Path.of(userHome, "桌面", ".env"),
                Path.of("C:", "Users", "Administrator", "Desktop", ".env")
        );
        for (Path candidate : candidates) {
            String value = read(candidate, key);
            if (notBlank(value)) return value.trim();
        }
        return "";
    }

    private static String read(Path file, String key) {
        if (file == null || !Files.isRegularFile(file)) return "";
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String normalized = line.replace("\uFEFF", "").trim();
                if (normalized.isBlank() || normalized.startsWith("#")) continue;
                int eq = normalized.indexOf('=');
                if (eq <= 0) continue;
                String name = normalized.substring(0, eq).trim();
                if (!key.equals(name)) continue;
                return unquote(normalized.substring(eq + 1).trim());
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String unquote(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
