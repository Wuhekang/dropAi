package com.dropai.rewrite.utils;

import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Native 降AI guards. Field, bookmark, and hyperlink paragraphs are left untouched.
 * Plain paragraphs are refilled by editing existing w:t nodes so paragraph and run
 * properties stay in place. Drawings stay on {@link DocxImageTextPreserver}.
 */
public final class DocxNativeStructureGuard {
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final Set<String> LOCKED = Set.of(
            "fldChar", "instrText", "fldSimple", "fldData", "hyperlink",
            "bookmarkStart", "bookmarkEnd",
            "del", "ins", "moveFrom", "moveTo"
    );

    private DocxNativeStructureGuard() {
    }

    public static boolean containsLockedStructure(XWPFParagraph paragraph) {
        return paragraph != null && containsLocked(paragraph.getCTP().getDomNode());
    }

    /**
     * Writes replacement into existing text nodes. Returns false without editing when the
     * paragraph holds a cross-reference, bookmark, hyperlink, or a run that is not plain text.
     */
    public static boolean replacePreservingRuns(XWPFParagraph paragraph, String replacement) {
        if (paragraph == null || replacement == null || replacement.isBlank()) {
            return false;
        }
        if (containsLockedStructure(paragraph) || DocxImageTextPreserver.containsGraphics(paragraph)) {
            return false;
        }
        List<CTText> slots = new ArrayList<>();
        for (CTR run : paragraph.getCTP().getRList()) {
            if (!isPlainTextRun(run.getDomNode())) {
                return false;
            }
            for (CTText text : run.getTList()) {
                if (!text.getStringValue().isEmpty()) {
                    slots.add(text);
                }
            }
        }
        if (slots.isEmpty()) {
            return false;
        }
        if (replacement.equals(paragraph.getText())) {
            return true;
        }
        List<String> original = slots.stream().map(CTText::getStringValue).toList();
        String[] values = new String[slots.size()];
        int sourceStart = 0;
        int targetStart = 0;
        for (int index = 0; index < slots.size(); index++) {
            String old = original.get(index);
            if (!CITATION_ONLY.matcher(old).matches()) continue;
            var tokens = CITATION.matcher(old).results().map(m -> Pattern.quote(m.group())).toList();
            var anchor = Pattern.compile(String.join("\\s*", tokens)).matcher(replacement);
            if (!anchor.find(targetStart)) return false;
            if (!distribute(original, values, sourceStart, index,
                    replacement.substring(targetStart, anchor.start()))) return false;
            values[index] = anchor.group();
            sourceStart = index + 1;
            targetStart = anchor.end();
        }
        if (!distribute(original, values, sourceStart, slots.size(), replacement.substring(targetStart))) return false;
        // Apply only after the whole plan is valid. Citation text stays in its original
        // superscript/font run; other text remains distributed over existing styled runs.
        for (int index = 0; index < slots.size(); index++) {
            slots.get(index).setStringValue(values[index]);
            slots.get(index).setSpace(SpaceAttribute.Space.PRESERVE);
        }
        return true;
    }

    private static final Pattern CITATION = Pattern.compile("\\[[0-9０-９][0-9０-９,，、\\-–—\\s]*\\]");
    private static final Pattern CITATION_ONLY = Pattern.compile("(?:\\s*\\[[0-9０-９][0-9０-９,，、\\-–—\\s]*\\]\\s*)+");

    private static boolean distribute(List<String> source, String[] values, int from, int to, String text) {
        if (from == to) return text.isEmpty();
        int total = 0;
        for (int i = from; i < to; i++) total += source.get(i).length();
        int weight = 0;
        int previous = 0;
        for (int i = from; i < to; i++) {
            weight += source.get(i).length();
            int end = i == to - 1 ? text.length() : (int) ((long) text.length() * weight / total);
            if (end > 0 && end < text.length() && Character.isLowSurrogate(text.charAt(end))) end--;
            end = Math.max(previous, end);
            values[i] = text.substring(previous, end);
            previous = end;
        }
        return true;
    }

    private static boolean containsLocked(Node node) {
        if (node.getNodeType() == Node.ELEMENT_NODE
                && W.equals(node.getNamespaceURI())
                && LOCKED.contains(node.getLocalName())) {
            return true;
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (containsLocked(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPlainTextRun(Node run) {
        for (Node child = run.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (!W.equals(child.getNamespaceURI())) {
                return false;
            }
            String name = child.getLocalName();
            // Line breaks and tabs are common in prose. They are not drawings.
            if (!"rPr".equals(name) && !"t".equals(name) && !"br".equals(name) && !"cr".equals(name)
                    && !"tab".equals(name) && !"lastRenderedPageBreak".equals(name)
                    && !"commentReference".equals(name)
                    && !"softHyphen".equals(name) && !"noBreakHyphen".equals(name)) {
                return false;
            }
        }
        return true;
    }
}
