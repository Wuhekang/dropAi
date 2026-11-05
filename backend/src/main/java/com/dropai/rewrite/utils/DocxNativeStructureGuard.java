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
 * Native 降AI guards. REF, PAGEREF, NOTEREF, caption SEQ/STYLEREF, other field
 * codes, bookmarks, and hyperlinks stay untouched. A TOC field that continues
 * across paragraphs is tracked by {@link TocFieldScan}, including entries that
 * do not repeat the field code. Plain paragraphs are refilled by editing existing
 * w:t nodes so paragraph and run properties stay in place. Drawings stay on
 * {@link DocxImageTextPreserver}.
 */
public final class DocxNativeStructureGuard {
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final Pattern TOC_INSTRUCTION = Pattern.compile("(?i)^\\s*TOC(?:\\s|$)");
    private static final Set<String> LOCKED = Set.of(
            "fldChar", "instrText", "delInstrText", "fldSimple", "fldData", "hyperlink",
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
            if (!"rPr".equals(name) && !"t".equals(name)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Walks body paragraphs in order. A TOC instruction opens a span that lasts until its
     * matching field end, so a later list of figures is skipped even after the leading
     * catalog has already closed. Nested PAGEREF/NOTEREF fields do not close that span.
     */
    public static final class TocFieldScan {
        private int depth;
        private int tocDepth = -1;

        public boolean observe(XWPFParagraph paragraph) {
            boolean alreadyInside = tocDepth >= 0;
            boolean sawToc = paragraph != null && visit(paragraph.getCTP().getDomNode());
            return alreadyInside || sawToc || tocDepth >= 0;
        }

        private boolean visit(Node node) {
            boolean saw = mark(node);
            for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
                saw = visit(child) || saw;
            }
            return saw;
        }

        private boolean mark(Node node) {
            if (node.getNodeType() != Node.ELEMENT_NODE || !W.equals(node.getNamespaceURI())) {
                return false;
            }
            String name = node.getLocalName();
            if ("fldChar".equals(name)) {
                String type = attribute(node, "fldCharType");
                if ("begin".equals(type)) {
                    depth++;
                } else if ("end".equals(type)) {
                    if (depth == tocDepth) {
                        tocDepth = -1;
                    }
                    depth = Math.max(0, depth - 1);
                }
                return false;
            }
            if ("instrText".equals(name) || "delInstrText".equals(name)) {
                if (TOC_INSTRUCTION.matcher(nodeText(node)).find() && depth > 0) {
                    tocDepth = depth;
                    return true;
                }
                return false;
            }
            if ("fldSimple".equals(name) && TOC_INSTRUCTION.matcher(attribute(node, "instr")).find()) {
                return true;
            }
            return false;
        }

        private static String attribute(Node node, String name) {
            if (node.getAttributes() == null) {
                return "";
            }
            Node value = node.getAttributes().getNamedItemNS(W, name);
            if (value == null) {
                value = node.getAttributes().getNamedItem(name);
            }
            return value == null || value.getNodeValue() == null ? "" : value.getNodeValue();
        }

        private static String nodeText(Node node) {
            if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                return node.getNodeValue() == null ? "" : node.getNodeValue();
            }
            StringBuilder value = new StringBuilder();
            for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
                value.append(nodeText(child));
            }
            return value.toString();
        }
    }
}
