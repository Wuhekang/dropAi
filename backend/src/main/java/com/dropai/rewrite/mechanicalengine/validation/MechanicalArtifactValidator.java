package com.dropai.rewrite.mechanicalengine.validation;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class MechanicalArtifactValidator {
    public ValidationReport validate(MechanicalProject project, Path root) {
        List<String> errors = new ArrayList<>();
        requireCompound(root.resolve("01_Model/Assembly.SLDASM"), "SolidWorks assembly", errors);
        for (MechanicalProject.CADSpecification part : project.getParts()) {
            requireCompound(root.resolve("01_Model/Parts/" + part.partNumber() + ".SLDPRT"), "SolidWorks part " + part.partNumber(), errors);
            requireDwg(root.resolve("03_Drawing/Parts_Drawing/" + part.partNumber() + ".DWG"), "part drawing " + part.partNumber(), errors);
        }
        requireStep(root.resolve("02_STEP/Assembly.STEP"), errors);
        requireDwg(root.resolve("03_Drawing/Assembly.DWG"), "assembly drawing", errors);
        requirePng(root.resolve("02_STEP/freecad-preview.png"), errors);
        requireFreeCadReceipt(root.resolve("02_STEP/freecad-validation.json"), errors);
        requirePdf(root.resolve("04_Document/Design_Report.pdf"), errors);
        if (project.getAssembly().getMates().size() < project.getAssembly().getComponents().size() - 1) errors.add("assembly mate count is incomplete");
        if (!freeCadAvailable()) errors.add("FreeCAD preview validator is unavailable");
        return new ValidationReport(errors.isEmpty(), errors);
    }

    private void requireCompound(Path file, String label, List<String> errors) {
        byte[] signature = bytes(file, 8, label, errors);
        byte[] expected = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        if (signature.length == 8 && !java.util.Arrays.equals(signature, expected)) errors.add(label + " is not a native SolidWorks compound document");
    }
    private void requireStep(Path file, List<String> errors) {
        String value = text(file, "STEP", errors);
        if (!value.contains("ISO-10303-21") || !value.contains("END-ISO-10303-21")) errors.add("STEP cannot be reopened as a complete exchange document");
    }
    private void requireDwg(Path file, String label, List<String> errors) {
        byte[] value = bytes(file, 6, label, errors);
        if (value.length == 6 && !new String(value, StandardCharsets.US_ASCII).startsWith("AC10")) errors.add(label + " signature is invalid");
    }
    private void requirePng(Path file, List<String> errors) {
        byte[] value = bytes(file, 8, "FreeCAD preview", errors);
        byte[] expected = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (value.length == 8 && !java.util.Arrays.equals(value, expected)) errors.add("FreeCAD preview is not a PNG image");
    }
    private void requireFreeCadReceipt(Path file, List<String> errors) {
        String value = text(file, "FreeCAD validation receipt", errors);
        if (!value.contains("\"passed\":true") && !value.contains("\"passed\": true")) {
            errors.add("FreeCAD did not confirm that Assembly.STEP reopened successfully");
        }
    }
    private void requirePdf(Path file, List<String> errors) {
        byte[] value = bytes(file, 5, "PDF report", errors);
        if (value.length == 5 && !new String(value, StandardCharsets.US_ASCII).equals("%PDF-")) errors.add("design report PDF signature is invalid");
    }
    private byte[] bytes(Path file, int length, String label, List<String> errors) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) < length) { errors.add(label + " is missing or empty"); return new byte[0]; }
            byte[] all = Files.readAllBytes(file); return java.util.Arrays.copyOf(all, length);
        } catch (Exception exception) { errors.add(label + " cannot be read"); return new byte[0]; }
    }
    private String text(Path file, String label, List<String> errors) {
        try { return Files.readString(file, StandardCharsets.ISO_8859_1); }
        catch (Exception exception) { errors.add(label + " is missing or unreadable"); return ""; }
    }
    private boolean freeCadAvailable() {
        String command = System.getenv("FREECAD_VALIDATION_COMMAND");
        return command != null && !command.isBlank();
    }
    public record ValidationReport(boolean passed, List<String> errors) {}
}
