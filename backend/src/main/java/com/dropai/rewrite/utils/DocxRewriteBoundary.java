package com.dropai.rewrite.utils;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFSDT;
import org.w3c.dom.Node;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Read-only TOC boundaries shared by humanize routes; a body heading is not required. */
public final class DocxRewriteBoundary {
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final Pattern TOC_INSTRUCTION = Pattern.compile("(?i)^\\s*TOC(?:\\s|$)");
    private static final Pattern MANUAL_ENTRY = Pattern.compile(
            "^[^。！？!?]{1,180}(?:\\t+|[.．·…]{2,}|\\s+)\\s*[0-9０-９IVXLCDMivxlcdmⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]+\\s*$");
    public static final String ABSTRACT_PREFIX_REGEX =
            "(?i:^[\\t 　]*(?:[\\[【（(][\\t 　]*(?:摘[\\t 　]*要|abstract)[\\t 　]*[\\]】）)][\\t 　]*[:：]?"
                    + "|(?:摘[\\t 　]*要|abstract)[\\t 　]*[:：]))";

    private DocxRewriteBoundary() { }

    public static Scope inspect(XWPFDocument document) {
        Map<IBodyElement, Integer> positions = new HashMap<>();
        int catalogStart = -1;
        int bodyStart = -1;
        FieldState fields = new FieldState();
        var elements = document.getBodyElements();
        for (int index = 0; index < elements.size(); index++) {
            IBodyElement element = elements.get(index);
            positions.put(element, index);
            if (bodyStart >= 0) continue;
            boolean catalog = false;
            boolean empty = false;
            if (element instanceof XWPFSDT control) {
                // SDT contents are atomic here: all nested entries remain outside the rewrite scope.
                catalog = isCatalogTitle(control.getContent().getText().split("\\R", 2)[0])
                        || control.getContent().getText().stripLeading().startsWith("目录\n");
                // The content-control gallery also identifies a TOC without a visible title.
                Node node = document.getDocument().getBody().getDomNode().getFirstChild();
                int sdtIndex = 0;
                int desired = 0;
                for (int previous = 0; previous < index; previous++) {
                    if (elements.get(previous) instanceof XWPFSDT) desired++;
                }
                while (node != null) {
                    if ("sdt".equals(node.getLocalName()) && W.equals(node.getNamespaceURI())) {
                        if (sdtIndex++ == desired) {
                            catalog |= containsTocMetadata(node);
                            break;
                        }
                    }
                    node = node.getNextSibling();
                }
            } else if (element instanceof XWPFParagraph paragraph) {
                String text = paragraph.getText().trim();
                boolean insideField = fields.tocDepth >= 0;
                fields.sawToc = false;
                fields.visit(paragraph.getCTP().getDomNode());
                catalog = insideField || fields.sawToc || fields.tocDepth >= 0
                        || isCatalogTitle(text) || isCatalogStyle(paragraph);
                if (catalogStart >= 0 && MANUAL_ENTRY.matcher(text).matches()) catalog = true;
                empty = text.isBlank();
            }
            if (catalog) {
                if (catalogStart < 0) catalogStart = index;
            } else if (catalogStart >= 0 && !empty) {
                bodyStart = index;
            }
        }
        return new Scope(positions, catalogStart, bodyStart < 0 ? elements.size() : bodyStart);
    }

    public static boolean isAbstractTitle(String text) {
        return text != null && text.replaceAll("[\\s　\\[\\]【】（）():：]", "")
                .matches("(?i)(摘要|abstract)");
    }

    public static boolean isInlineAbstract(String text) {
        return text != null && text.matches(ABSTRACT_PREFIX_REGEX + "\\s*\\S[\\s\\S]*");
    }

    public static boolean isKeywordLine(String text) {
        return text != null && text.matches(
                "(?is)^\\s*[\\[【（(]?\\s*(关键词|关键字|key\\s*words)\\s*(?:[\\]】）)]|[:：]|$).*"
        );
    }

    private static boolean isCatalogTitle(String text) {
        return text != null && text.replaceAll("[\\s　]", "").matches("(?i)(目录|contents|tableofcontents)");
    }

    private static boolean isCatalogStyle(XWPFParagraph paragraph) {
        var p = paragraph.getCTP();
        if (!p.isSetPPr() || !p.getPPr().isSetPStyle()) return false;
        String id = p.getPPr().getPStyle().getVal();
        if (isTocStyleName(id)) return true;
        var styles = paragraph.getDocument().getStyles();
        var style = styles == null ? null : styles.getStyle(id);
        return style != null && isTocStyleName(style.getName());
    }

    private static boolean isTocStyleName(String value) {
        return value != null && value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT)
                .matches("(?:toc[1-9]?|目录[1-9]?)");
    }

    private static boolean containsTocMetadata(Node node) {
        if ("docPartGallery".equals(node.getLocalName())
                && isCatalogTitle(attribute(node, "val"))) return true;
        if ("instrText".equals(node.getLocalName())
                && TOC_INSTRUCTION.matcher(nodeText(node)).find()) return true;
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (containsTocMetadata(child)) return true;
        }
        return false;
    }

    private static String attribute(Node node, String name) {
        Node value = node.getAttributes() == null ? null : node.getAttributes().getNamedItemNS(W, name);
        return value == null ? "" : value.getNodeValue();
    }

    private static String nodeText(Node node) {
        if (node.getNodeType() == Node.TEXT_NODE) return node.getNodeValue();
        StringBuilder value = new StringBuilder();
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            value.append(nodeText(child));
        }
        return value.toString();
    }

    private static final class FieldState {
        int depth;
        int tocDepth = -1;
        boolean sawToc;

        void visit(Node node) {
            if ("fldChar".equals(node.getLocalName())) {
                String type = attribute(node, "fldCharType");
                if ("begin".equals(type)) depth++;
                if ("end".equals(type)) {
                    if (depth == tocDepth) tocDepth = -1;
                    depth = Math.max(0, depth - 1);
                }
            } else if ("instrText".equals(node.getLocalName())
                    && TOC_INSTRUCTION.matcher(nodeText(node)).find()) {
                sawToc = true;
                if (depth > 0) tocDepth = depth;
            } else if ("fldSimple".equals(node.getLocalName())
                    && TOC_INSTRUCTION.matcher(attribute(node, "instr")).find()) {
                sawToc = true;
            }
            for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) visit(child);
        }
    }

    public record Scope(Map<IBodyElement, Integer> positions, int catalogStart, int bodyStart) {
        public boolean hasCatalog() { return catalogStart >= 0; }
        public boolean inCatalog(IBodyElement element) {
            int index = positions.getOrDefault(element, -1);
            return hasCatalog() && index >= catalogStart && index < bodyStart;
        }
        public boolean afterCatalog(IBodyElement element) {
            return hasCatalog() && positions.getOrDefault(element, -1) >= bodyStart;
        }
    }
}
