package com.dropai.rewrite.service.impl;

import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Keep citation run formatting (including split-run superscripts) when replacing specialist prose. */
final class GezidaParagraphWriter {
    private static final Pattern CITATION = Pattern.compile("\\[[0-9０-９][0-9０-９,，、\\-–—\\s]*\\]");
    private static final Pattern EMBEDDED_CONTENT = Pattern.compile("<(?:[\\w]+:)?(?:drawing|pict|oMath|oMathPara|fldChar|hyperlink|footnoteReference|endnoteReference|object)\\b");
    private record Span(int start, int end, CTRPr properties) { }
    private record Piece(String text, CTRPr properties) { }

    static void replace(XWPFParagraph paragraph, String rewritten) {
        if (EMBEDDED_CONTENT.matcher(paragraph.getCTP().xmlText()).find()) {
            throw new IllegalStateException("格子达段落含内嵌对象或域，保留原段防止格式损坏");
        }
        String original = paragraph.getText();
        List<Span> spans = new ArrayList<>();
        int offset = 0;
        XWPFRun base = null;
        for (XWPFRun run : paragraph.getRuns()) {
            int length = run.text().length();
            spans.add(new Span(offset, offset + length, copyProperties(run)));
            offset += length;
            if (base == null || length > base.text().length()) base = run;
        }
        CTRPr baseProperties = base == null ? null : copyProperties(base);
        var sourceCitations = CITATION.matcher(original);
        var targetCitations = CITATION.matcher(rewritten);
        List<Piece> pieces = new ArrayList<>();
        int targetOffset = 0;
        while (sourceCitations.find()) {
            if (!targetCitations.find() || !sourceCitations.group().equals(targetCitations.group())) {
                throw new IllegalStateException("格子达引文样式恢复失败：引文内容或顺序变化");
            }
            pieces.add(new Piece(rewritten.substring(targetOffset, targetCitations.start()), baseProperties));
            for (Span span : spans) {
                int start = Math.max(sourceCitations.start(), span.start());
                int end = Math.min(sourceCitations.end(), span.end());
                if (start < end) pieces.add(new Piece(original.substring(start, end), span.properties()));
            }
            targetOffset = targetCitations.end();
        }
        if (targetCitations.find()) throw new IllegalStateException("格子达引文样式恢复失败：新增引文");
        pieces.add(new Piece(rewritten.substring(targetOffset), baseProperties));
        // Construct and validate before mutating the paragraph.
        String rebuilt = pieces.stream().map(Piece::text).collect(java.util.stream.Collectors.joining());
        if (!rewritten.equals(rebuilt)) throw new IllegalStateException("格子达引文样式恢复失败：文本映射不完整");
        for (int i = paragraph.getRuns().size() - 1; i >= 0; i--) paragraph.removeRun(i);
        for (Piece piece : pieces) {
            if (piece.text().isEmpty()) continue;
            XWPFRun run = paragraph.createRun();
            if (piece.properties() != null) run.getCTR().setRPr((CTRPr) piece.properties().copy());
            run.setText(piece.text());
        }
    }

    private static CTRPr copyProperties(XWPFRun run) {
        return run.getCTR().isSetRPr() ? (CTRPr) run.getCTR().getRPr().copy() : null;
    }
}
