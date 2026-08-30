package com.dropai.rewrite.service.ppt.rendering.bundle.v1;

import com.dropai.rewrite.service.ppt.rendering.canonical.v1.RenderPlanCanonicalizer;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

final class BundleSupport {
    static final String PLAN_FILE = "render-plan.json";
    static final String PLAN_HASH_FILE = "render-plan.sha256";
    static final String GENERATION_MANIFEST_FILE = "generation-manifest.json";
    static final String ASSET_MANIFEST_FILE = "asset-manifest.json";
    static final String FONT_MANIFEST_FILE = "font-manifest.json";
    static final String CURRENT_FILE = "current";
    static final String PENDING_FILE = "pending";
    static final String REVISIONS_DIRECTORY = "revisions";
    private static final long MAX_JSON_BYTES = 64L * 1024L * 1024L;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final RenderPlanCanonicalizer CANONICALIZER = new RenderPlanCanonicalizer();

    private BundleSupport() {
    }

    static byte[] canonical(JsonNode document) {
        return CANONICALIZER.canonicalBytes(document);
    }

    static String sha256(byte[] bytes) {
        try {
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static void writeNew(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
    }

    static void writeCanonical(Path file, JsonNode document) throws IOException {
        writeNew(file, canonical(document));
    }

    static ObjectNode readCanonicalObject(Path file) {
        byte[] bytes = readRequired(file, MAX_JSON_BYTES);
        try {
            JsonNode node = MAPPER.readTree(bytes);
            if (!node.isObject()) {
                throw new RenderPlanBundleException(PptQualityCode.SCHEMA_INVALID,
                        file.getFileName() + " must contain a JSON object");
            }
            if (!java.util.Arrays.equals(bytes, canonical(node))) {
                throw new RenderPlanBundleException(PptQualityCode.SCHEMA_INVALID,
                        file.getFileName() + " is not canonical JSON");
            }
            return (ObjectNode) node;
        } catch (IOException exception) {
            throw new RenderPlanBundleException(PptQualityCode.SCHEMA_INVALID,
                    "Cannot parse " + file.getFileName(), exception);
        }
    }

    static byte[] readRequired(Path file, long maxBytes) {
        try {
            if (Files.isSymbolicLink(file)
                    || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                        "Required bundle file is missing or symbolic: " + file.getFileName());
            }
            long size = Files.size(file);
            if (size < 1 || size > maxBytes) {
                throw new RenderPlanBundleException(PptQualityCode.SCHEMA_INVALID,
                        "Bundle file size is invalid: " + file.getFileName());
            }
            return Files.readAllBytes(file);
        } catch (RenderPlanBundleException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                    "Cannot read bundle file " + file.getFileName(), exception);
        }
    }

    static String readHashFile(Path file) {
        String value = new String(readRequired(file, 256), StandardCharsets.UTF_8);
        if (!value.endsWith("\n") || value.contains("\r")) {
            throw new RenderPlanBundleException(PptQualityCode.INVALID_HASH,
                    file.getFileName() + " must be an LF-terminated hash");
        }
        value = value.substring(0, value.length() - 1);
        requireHash(value, file.getFileName().toString());
        return value;
    }

    static Path resolveBundlePath(Path root, String bundlePath) {
        if (!bundlePath.matches("^(?!/)(?![A-Za-z]:)(?!.*\\\\)(?!.*(?:^|/)\\.\\.(?:/|$))assets/[A-Za-z0-9._/-]+$")) {
            throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                    "Unsafe asset bundlePath: " + bundlePath);
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(bundlePath.replace('/', java.io.File.separatorChar)).normalize();
        if (!resolved.startsWith(normalizedRoot.resolve("assets"))) {
            throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                    "Asset path escapes bundle: " + bundlePath);
        }
        return resolved;
    }

    static void requireNoSymbolicPath(Path root, Path file) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedFile = file.toAbsolutePath().normalize();
        if (!normalizedFile.startsWith(normalizedRoot)) {
            throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                    "Bundle file escapes its immutable revision");
        }
        try {
            Path realRoot = normalizedRoot.toRealPath();
            Path cursor = normalizedRoot;
            for (Path segment : normalizedRoot.relativize(normalizedFile)) {
                cursor = cursor.resolve(segment);
                Path expected = realRoot.resolve(normalizedRoot.relativize(cursor)).normalize();
                if (Files.isSymbolicLink(cursor) || !cursor.toRealPath().equals(expected)) {
                    throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                            "Symbolic or reparse paths are forbidden in a RenderPlan bundle: " + cursor);
                }
            }
        } catch (RenderPlanBundleException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                    "Cannot verify bundle path containment: " + normalizedFile, exception);
        }
    }

    static Path requireBundleRoot(Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                    "Bundle root is missing or symbolic: " + normalized);
        }
        Path parent = normalized.getParent();
        if (parent == null) {
            throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                    "Bundle root has no parent: " + normalized);
        }
        try {
            if (!normalized.toRealPath().equals(parent.toRealPath().resolve(normalized.getFileName()).normalize())) {
                throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                        "Bundle root must not be a symbolic or reparse directory: " + normalized);
            }
        } catch (RenderPlanBundleException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                    "Cannot verify bundle root: " + normalized, exception);
        }
        return normalized;
    }

    static Path requireDirectDirectory(Path parent, Path directory, String label) {
        Path normalizedParent = parent.toAbsolutePath().normalize();
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        if (!Objects.equals(normalizedDirectory.getParent(), normalizedParent)
                || Files.isSymbolicLink(normalizedDirectory)
                || !Files.isDirectory(normalizedDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                    label + " is missing, symbolic or outside its parent");
        }
        try {
            Path expected = normalizedParent.toRealPath()
                    .resolve(normalizedDirectory.getFileName()).normalize();
            if (!normalizedDirectory.toRealPath().equals(expected)) {
                throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                        label + " must not be a reparse directory");
            }
            return normalizedDirectory;
        } catch (RenderPlanBundleException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                    "Cannot verify " + label, exception);
        }
    }

    static String requiredSchema(ObjectNode document, String expected) {
        String actual = requiredText(document, "schemaVersion");
        if (!expected.equals(actual)) {
            throw new RenderPlanBundleException(PptQualityCode.UNSUPPORTED_SCHEMA_VERSION,
                    "Expected " + expected + " but found " + actual);
        }
        return actual;
    }

    static String requiredText(ObjectNode owner, String field) {
        JsonNode value = owner.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new RenderPlanBundleException(PptQualityCode.SCHEMA_INVALID,
                    field + " must be a non-blank string");
        }
        return value.textValue();
    }

    static int requiredInt(ObjectNode owner, String field) {
        JsonNode value = owner.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new RenderPlanBundleException(PptQualityCode.SCHEMA_INVALID,
                    field + " must be an integer");
        }
        return value.intValue();
    }

    static boolean requiredBoolean(ObjectNode owner, String field) {
        JsonNode value = owner.path(field);
        if (!value.isBoolean()) {
            throw new RenderPlanBundleException(PptQualityCode.SCHEMA_INVALID,
                    field + " must be boolean");
        }
        return value.booleanValue();
    }

    static ObjectNode requiredObject(ObjectNode owner, String field) {
        JsonNode value = owner.path(field);
        if (!value.isObject()) {
            throw new RenderPlanBundleException(PptQualityCode.SCHEMA_INVALID,
                    field + " must be an object");
        }
        return (ObjectNode) value;
    }

    static void requireHash(String value, String field) {
        if (value == null || !value.matches("^sha256:[a-f0-9]{64}$")) {
            throw new RenderPlanBundleException(PptQualityCode.INVALID_HASH,
                    field + " is not a SHA-256 contract hash");
        }
    }

    static void deleteTree(Path directory) {
        if (directory == null || !Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Path root=directory.toAbsolutePath().normalize();
        try {
            Path parent=root.getParent();
            if(parent==null)return;
            Path parentReal=parent.toRealPath();
            Path rootReal=root.toRealPath();
            if(!Objects.equals(rootReal,parentReal.resolve(root.getFileName()).normalize()))return;
            Files.walkFileTree(root,new SimpleFileVisitor<>(){
                @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attributes)throws IOException{
                    return insideRealRoot(dir,rootReal)?FileVisitResult.CONTINUE:FileVisitResult.SKIP_SUBTREE;
                }
                @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attributes)throws IOException{
                    if(insideRealRoot(file,rootReal))Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path dir,IOException error)throws IOException{
                    if(error==null&&insideRealRoot(dir,rootReal))Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Preserve the publication error. Unsafe or concurrently replaced paths are left isolated.
        }
    }

    private static boolean insideRealRoot(Path path,Path rootReal){
        try{return path.toRealPath().startsWith(rootReal);}
        catch(IOException exception){return false;}
    }
}
