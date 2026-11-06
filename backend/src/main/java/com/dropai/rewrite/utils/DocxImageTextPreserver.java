package com.dropai.rewrite.utils;

import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Text-only refill for graphic-bearing paragraphs; never remove/recreate their runs. */
public final class DocxImageTextPreserver {
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String MC = "http://schemas.openxmlformats.org/markup-compatibility/2006";
    private static final Set<String> GRAPHICS = Set.of("drawing", "pict", "object");
    private static final Set<String> MARKERS = Set.of("bookmarkStart", "bookmarkEnd", "proofErr");

    private DocxImageTextPreserver() { }

    public static boolean containsGraphics(XWPFParagraph paragraph) {
        return containsGraphics(paragraph.getCTP().getDomNode());
    }

    private static boolean containsGraphics(Node node) {
        if (isGraphic(node)) return true;
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (containsGraphics(child)) return true;
        }
        return false;
    }

    private static boolean isGraphic(Node node) {
        return W.equals(node.getNamespaceURI()) && GRAPHICS.contains(node.getLocalName())
                || MC.equals(node.getNamespaceURI()) && "AlternateContent".equals(node.getLocalName());
    }

    /**
     * Returns false without touching the XML when visible text cannot be mapped safely.
     * Fields, hyperlinks, revisions, text boxes and manual breaks require a range-aware editor;
     * retaining the whole paragraph is preferable to flattening them or losing a picture.
     * Graphic subtrees (including WPS/VML and AlternateContent fallbacks) stay opaque.
     */
    public static boolean replaceText(XWPFParagraph paragraph, String replacement) {
        if (replacement == null || replacement.isBlank()) return false;
        if (replacement.equals(paragraph.getText().trim())) return true;
        Node root = paragraph.getCTP().getDomNode();
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            if (isGraphic(child)) continue;
            if (!W.equals(child.getNamespaceURI())) return false;
            if ("pPr".equals(child.getLocalName()) || MARKERS.contains(child.getLocalName())) continue;
            if (!"r".equals(child.getLocalName())) return false;
            for (Node part = child.getFirstChild(); part != null; part = part.getNextSibling()) {
                if (part.getNodeType() != Node.ELEMENT_NODE || isGraphic(part)) continue;
                if (!W.equals(part.getNamespaceURI())
                        || !("rPr".equals(part.getLocalName()) || "t".equals(part.getLocalName()))) return false;
            }
        }
        List<CTText> slots = new ArrayList<>();
        StringBuilder original = new StringBuilder();
        for (var run : paragraph.getCTP().getRList()) {
            for (CTText text : run.getTList()) {
                // Keep intentionally empty text nodes empty (often used next to drawings).
                if (!text.getStringValue().isEmpty()) {
                    slots.add(text);
                    original.append(text.getStringValue());
                }
            }
        }
        if (slots.isEmpty() || !original.toString().equals(paragraph.getText())) return false;

        // Preserve leading/trailing paragraph whitespace excluded by target extraction.
        String source = original.toString();
        int start = 0, end = source.length();
        while (start < end && source.charAt(start) <= ' ') start++;
        while (end > start && source.charAt(end - 1) <= ' ') end--;
        String text = source.substring(0, start) + replacement + source.substring(end);
        int originalPoints = source.codePointCount(0, source.length());
        int newPoints = text.codePointCount(0, text.length());
        Set<Integer> graphicBoundaries = graphicBoundaries(root);
        List<String> pieces = new ArrayList<>(slots.size());
        int consumed = 0, previous = 0, previousPoint = 0;
        for (CTText slot : slots) {
            String old = slot.getStringValue();
            consumed += old.codePointCount(0, old.length());
            int boundary = (int) ((long) consumed * newPoints / originalPoints);
            if (pieces.size() + 1 < slots.size() && graphicBoundaries.contains(pieces.size() + 1)) {
                // An inline picture must not be inserted into the middle of a rewritten word
                // when a nearby clause/word boundary is available.
                boundary = nearestTextBoundary(text, boundary);
            }
            boundary = Math.max(previousPoint, boundary);
            int offset = text.offsetByCodePoints(0, boundary);
            pieces.add(text.substring(previous, offset));
            previous = offset;
            previousPoint = boundary;
        }
        // Only w:t values change. Image nodes, relationships, anchor sizes/positions,
        // run properties, bookmarks, tables and media bytes are never reconstructed.
        for (int index = 0; index < slots.size(); index++) {
            slots.get(index).setStringValue(pieces.get(index));
            slots.get(index).setSpace(SpaceAttribute.Space.PRESERVE);
        }
        return true;
    }

    private static Set<Integer> graphicBoundaries(Node paragraph) {
        Set<Integer> result = new HashSet<>();
        int slotsSeen = 0;
        for (Node child = paragraph.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (isGraphic(child)) result.add(slotsSeen);
            if (!W.equals(child.getNamespaceURI()) || !"r".equals(child.getLocalName())) continue;
            for (Node part = child.getFirstChild(); part != null; part = part.getNextSibling()) {
                if (isGraphic(part)) result.add(slotsSeen);
                if (W.equals(part.getNamespaceURI()) && "t".equals(part.getLocalName())) {
                    String value = part.getFirstChild() == null ? null : part.getFirstChild().getNodeValue();
                    if (value != null && !value.isEmpty()) slotsSeen++;
                }
            }
        }
        return result;
    }

    private static int nearestTextBoundary(String text, int proposed) {
        int best = proposed, distance = Integer.MAX_VALUE, point = 0, previous = -1;
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);
            point++;
            boolean insideNumber = ".,:".indexOf(cp) >= 0 && Character.isDigit(previous)
                    && offset < text.length() && Character.isDigit(text.codePointAt(offset));
            if (!insideNumber && (Character.isWhitespace(cp) || "，。！？；：,.!?;:".indexOf(cp) >= 0)) {
                int candidateDistance = Math.abs(point - proposed);
                if (candidateDistance < distance) {
                    best = point;
                    distance = candidateDistance;
                }
            }
            previous = cp;
        }
        return best;
    }
}
