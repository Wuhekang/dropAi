package com.dropai.rewrite.mechanicalengine.service;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class MechanicalDocumentAgent {
    public void generate(MechanicalProject project, Path output) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4); document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                text(content, bold(), 18, 48, 795, "DropAI Mechanical Engineering Report");
                int y = 758;
                y = line(content, y, "1 Product Introduction: " + project.getProductName());
                y = line(content, y, "2 Requirement Analysis: " + String.join(", ", project.getRequirement().getFunctions()));
                y = line(content, y, "3 Concept Design: " + project.getConcept().getSelectedConcept());
                y = line(content, y, "4 Mechanical Architecture: " + String.join(", ", project.getConcept().getModules()));
                y = line(content, y, "5 Parts: " + project.getParts().size() + " feature-based manufactured parts");
                y = line(content, y, "6 Materials and Processes: " + project.getParts().stream().map(part -> part.material() + "/" + part.manufacturing()).distinct().toList());
                if (project.getAnalysisReport() != null) {
                    y = line(content, y, "7 Analysis: load " + format(project.getAnalysisReport().governingLoadN()) + " N, stress " + format(project.getAnalysisReport().estimatedStressMpa()) + " MPa");
                    y = line(content, y, "   Safety factor " + format(project.getAnalysisReport().safetyFactor()) + "; " + project.getAnalysisReport().conclusion());
                }
                line(content, y, "8 Summary: prototype, tolerance review, and formal verification are required before production release.");
            }
            document.save(output.toFile());
        }
    }

    private int line(PDPageContentStream content, int y, String value) throws Exception { text(content, regular(), 10, 48, y, value); return y - 34; }
    private void text(PDPageContentStream content, PDType1Font font, int size, float x, float y, String value) throws Exception {
        content.beginText(); content.setFont(font, size); content.newLineAtOffset(x, y);
        content.showText(value.replaceAll("[^\\x20-\\x7E]", "?")); content.endText();
    }
    private String format(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }
    private PDType1Font regular() { return new PDType1Font(Standard14Fonts.FontName.HELVETICA); }
    private PDType1Font bold() { return new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD); }
}
