package com.dropai.rewrite.mechanicalengine.service;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class MechanicalPackageBuilder {
    public byte[] build(Path root) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output)) {
            for (String directory : new String[]{"01_Model", "02_STEP", "03_Drawing", "04_Document"}) {
                Path source = root.resolve(directory);
                if (!Files.isDirectory(source)) throw new IllegalStateException("PACKAGE_SOURCE_MISSING: " + directory);
                try (var files = Files.walk(source)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        String entryName = root.relativize(file).toString().replace('\\', '/');
                        zip.putNextEntry(new ZipEntry(entryName));
                        Files.copy(file, zip);
                        zip.closeEntry();
                    }
                }
            }
            zip.finish();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("MECHANICAL_PACKAGE_FAILED: " + exception.getMessage(), exception);
        }
    }
}
