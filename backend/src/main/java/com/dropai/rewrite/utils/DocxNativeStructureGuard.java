package com.dropai.rewrite.utils;

import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        slots.get(0).setStringValue(replacement);
        slots.get(0).setSpace(SpaceAttribute.Space.PRESERVE);
        for (int index = 1; index < slots.size(); index++) {
            slots.get(index).setStringValue("");
            slots.get(index).setSpace(SpaceAttribute.Space.PRESERVE);
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
                    && !"softHyphen".equals(name) && !"noBreakHyphen".equals(name)) {
                return false;
            }
        }
        return true;
    }
}
