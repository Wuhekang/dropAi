package com.dropai.rewrite.external;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.springframework.stereotype.Service;
import org.w3c.dom.Node;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * DOCX processor used only by the opt-in Daya profile.
 * Tables, media, headings and trailing reference sections are never submitted.
 */
@Service
public class PlatformDoubaoDocumentProcessor {
    static final int DAYA_MAX_BATCH_PARAGRAPHS = 4;
    static final int DAYA_MAX_BATCH_CHARACTERS = 1800;
    private static final int MIN_PARAGRAPH_CHARACTERS = 24;
    private static final Pattern BODY_HEADING = Pattern.compile(
            "^(?:第[一二三四五六七八九十百]+章(?:\\s+.*)?|[1-9][0-9]*(?:[.．][0-9]+){0,3}[、.．\\s]+.{1,80})$");
    private static final Pattern ANY_NUMBERED_HEADING = Pattern.compile(
            "^(?:第[一二三四五六七八九十百]+[章节篇].*|[一二三四五六七八九十]+、.{1,80}|[0-9]+(?:[.．][0-9]+){0,4}[、.．\\s]+.{1,100})$");
    private static final Pattern CATALOG_LINE = Pattern.compile("^.+[.·•…]{2,}\\s*[0-9０-９]+\\s*$");
    private static final Pattern REFERENCE_LINE = Pattern.compile("^\\s*\\[[0-9０-９]+].*$");
    private static final Pattern CAPTION_LINE = Pattern.compile(
            "^(?:图|表|公式)\\s*[0-9０-９]+(?:[.．\\-—][0-9０-９]+)*(?:\\s+.*)?$");

    private final PlatformDoubaoRewriteGateway gateway;
    private final PlatformDocumentTextProtector protector;

    public PlatformDoubaoDocumentProcessor(PlatformDoubaoRewriteGateway gateway,
                                           PlatformDocumentTextProtector protector) {
        this.gateway = gateway;
        this.protector = protector;
    }

    public boolean configured() {
        return gateway.configured();
    }

    public ProcessingResult process(Path input, Path output,
                                    XuejiePlatform platform, XuejieRewriteMode mode,
                                    ProgressListener listener) {
        ProgressListener progress = listener == null ? ProgressListener.NOOP : listener;
        if (platform != XuejiePlatform.DAYA) throw new IllegalArgumentException("仅支持大雅平台");
        try (InputStream stream = Files.newInputStream(input);
             XWPFDocument document = new XWPFDocument(stream)) {
            List<Target> targets = collectDayaTargets(document);
            if (targets.isEmpty()) {
                throw new IllegalArgumentException("未识别到可处理的正文自然语言段落");
            }
            progress.update(targets.size(), 0, 0, "已筛选正文段落，正在加载平台 Skill");

            List<Batch> batches = batches(targets);
            AtomicInteger tokenSequence = new AtomicInteger();
            AtomicInteger styleSequence = new AtomicInteger();
            int processed = 0;
            int rewritten = 0;
            int failed = 0;
            List<String> failureMessages = new ArrayList<>();

            for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
                Batch batch = batches.get(batchIndex);
                Map<String, PlatformDocumentTextProtector.ProtectedText> protectedById = new LinkedHashMap<>();
                Map<String, StyledParagraph> styledById = new LinkedHashMap<>();
                List<PlatformDoubaoRewriteGateway.Segment> segments = new ArrayList<>();
                for (Target target : batch.targets()) {
                    StyledParagraph styledParagraph = styledParagraphFor(target, styleSequence);
                    PlatformDocumentTextProtector.ProtectedText protectedText =
                            protector.protect(styledParagraph.modelText(), tokenSequence);
                    styledById.put(target.id(), styledParagraph);
                    protectedById.put(target.id(), protectedText);
                    segments.add(new PlatformDoubaoRewriteGateway.Segment(
                            target.id(), protectedText.text(), target.context()));
                }

                try {
                    Map<String, String> responses = gateway.rewriteBatch(segments, platform, mode);
                    for (Target target : batch.targets()) {
                        try {
                            String protectedStylesRestored = protectedById.get(target.id())
                                    .validateAndRestore(responses.get(target.id()));
                            StyledRestore restored = styledById.get(target.id()).restore(protectedStylesRestored);
                            validateCandidate(target.originalText(), restored.text());
                            if (target.originalText().equals(restored.text())) continue;
                            replaceParagraphText(target.paragraph(), restored);
                            removeMergedListStructure(target);
                            rewritten++;
                        } catch (RuntimeException validationFailure) {
                            failed++;
                            if (failureMessages.size() < 3) failureMessages.add(compact(validationFailure.getMessage()));
                        }
                    }
                } catch (RuntimeException batchFailure) {
                    failed += batch.targets().size();
                    if (failureMessages.size() < 3) failureMessages.add(compact(batchFailure.getMessage()));
                }
                processed += batch.targets().size();
                progress.update(targets.size(), processed, rewritten,
                        platform.remoteName() + " Skill 处理中：" + (batchIndex + 1) + "/" + batches.size() + " 批");
            }

            if (rewritten == 0) {
                throw new IllegalStateException("平台 Skill 未生成任何通过保护校验的段落"
                        + (failureMessages.isEmpty() ? "" : "：" + String.join("；", failureMessages)));
            }
            writeAtomically(document, output);
            return new ProcessingResult(targets.size(), processed, rewritten, failed, List.copyOf(failureMessages));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("平台 DOCX 处理失败：" + compact(exception.getMessage()), exception);
        }
    }

    /**
     * The supplied Daya reports include Chinese and English abstracts in their chapter detail.
     * Select both abstracts and the body while keeping keywords, the catalog, references and
     * trailing structural sections outside the model boundary.
     */
    List<Target> collectDayaTargets(XWPFDocument document) {
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        List<Target> targets = new ArrayList<>();
        DayaSection section = DayaSection.SKIP;
        String context = "正文";
        boolean bodyStarted = false;

        for (int index = 0; index < paragraphs.size(); index++) {
            XWPFParagraph paragraph = paragraphs.get(index);
            String text = normalize(paragraph.getText());
            String compactText = text.replaceAll("\\s+", "");

            if (isChineseAbstractTitle(text)) {
                section = DayaSection.INCLUDE;
                context = "中文摘要";
                continue;
            }
            if (isEnglishAbstractTitle(text)) {
                section = DayaSection.INCLUDE;
                context = "英文摘要";
                continue;
            }
            if (isKeywordLine(text) || isCatalogTitle(text)) {
                section = DayaSection.SKIP;
                continue;
            }
            if (isReferenceTitle(text)) break;
            if (isAcknowledgementsTitle(text) || isProtectedTrailingTitle(compactText)) {
                section = DayaSection.SKIP;
                continue;
            }
            if (section == DayaSection.INCLUDE) {
                List<XWPFParagraph> enumerationGroup = collectDayaEnumerationGroup(paragraphs, index);
                if (!enumerationGroup.isEmpty()) {
                    String mergedText = mergedEnumerationText(enumerationGroup);
                    targets.add(new Target("p" + index, index, enumerationGroup.get(0),
                            mergedText, context, enumerationGroup, DayaPreparation.MERGED_LIST));
                    index += enumerationGroup.size() - 1;
                    continue;
                }
            }
            if (BODY_HEADING.matcher(text).matches() && !isCatalogStyle(paragraph)) {
                bodyStarted = true;
                section = DayaSection.INCLUDE;
                context = text;
                continue;
            }
            if (bodyStarted && isHeadingStyle(paragraph)) {
                context = text.isBlank() ? context : text;
                continue;
            }
            if (section == DayaSection.INCLUDE && isDayaCandidate(paragraph, text)) {
                String prepared = DayaEnumerationRules.normalizeInlineNumericEnumeration(text);
                DayaPreparation preparation = prepared.equals(text)
                        ? DayaPreparation.NONE : DayaPreparation.INLINE_NUMERIC;
                targets.add(new Target("p" + index, index, paragraph,
                        prepared, context, List.of(paragraph), preparation));
            }
        }
        return targets;
    }

    private boolean isDayaCandidate(XWPFParagraph paragraph, String text) {
        if (text.length() < MIN_PARAGRAPH_CHARACTERS) return false;
        if (countCjk(text) < 10 && countLatinLetters(text) < 40) return false;
        if (isHeadingStyle(paragraph)) return false;
        if (ANY_NUMBERED_HEADING.matcher(text).matches() && text.length() <= 30) return false;
        if (!hasOnlyDayaTextRuns(paragraph)) return false;
        if (CATALOG_LINE.matcher(text).matches() || REFERENCE_LINE.matcher(text).matches()
                || CAPTION_LINE.matcher(text).matches()) return false;
        if (isCatalogTitle(text) || isKeywordLine(text) || isAbstractTitle(text)
                || isReferenceTitle(text) || isAcknowledgementsTitle(text)) return false;
        return !isTechnicalFragment(text);
    }

    private List<XWPFParagraph> collectDayaEnumerationGroup(
            List<XWPFParagraph> paragraphs, int startIndex) {
        if (startIndex < 0 || startIndex >= paragraphs.size()) return List.of();
        XWPFParagraph first = paragraphs.get(startIndex);
        String firstText = normalize(first.getText());
        boolean automatic = hasAutomaticNumbering(first);
        if (!automatic && !DayaEnumerationRules.isLeadingListItem(firstText)) return List.of();
        if (!isDayaEnumerationItemCandidate(first, firstText)) return List.of();

        String numberingKey = automatic ? numberingKey(first) : "text";
        List<XWPFParagraph> group = new ArrayList<>();
        for (int index = startIndex; index < paragraphs.size(); index++) {
            XWPFParagraph paragraph = paragraphs.get(index);
            String text = normalize(paragraph.getText());
            boolean matchingKind = automatic
                    ? hasAutomaticNumbering(paragraph) && numberingKey.equals(numberingKey(paragraph))
                    : !hasAutomaticNumbering(paragraph) && DayaEnumerationRules.isLeadingListItem(text);
            if (!matchingKind || !isDayaEnumerationItemCandidate(paragraph, text)) break;
            group.add(paragraph);
        }
        if (group.size() < 2) return List.of();
        String merged = mergedEnumerationText(group);
        if (merged.length() < MIN_PARAGRAPH_CHARACTERS
                || (countCjk(merged) < 10 && countLatinLetters(merged) < 40)) {
            return List.of();
        }
        return List.copyOf(group);
    }

    private boolean isDayaEnumerationItemCandidate(XWPFParagraph paragraph, String text) {
        if (text.isBlank() || isHeadingStyle(paragraph) || isCatalogStyle(paragraph)) return false;
        if (!hasOnlyDayaTextRuns(paragraph)) return false;
        if (CATALOG_LINE.matcher(text).matches() || REFERENCE_LINE.matcher(text).matches()
                || CAPTION_LINE.matcher(text).matches()) return false;
        if (isCatalogTitle(text) || isKeywordLine(text) || isAbstractTitle(text)
                || isReferenceTitle(text) || isAcknowledgementsTitle(text) || isTrailingSection(text)) {
            return false;
        }
        return !isTechnicalFragment(text) && (countCjk(text) >= 2 || countLatinLetters(text) >= 12);
    }

    private boolean hasAutomaticNumbering(XWPFParagraph paragraph) {
        return paragraph.getCTP().isSetPPr()
                && paragraph.getCTP().getPPr().isSetNumPr()
                && paragraph.getCTP().getPPr().getNumPr().isSetNumId();
    }

    private String numberingKey(XWPFParagraph paragraph) {
        if (!hasAutomaticNumbering(paragraph)) return "";
        var numbering = paragraph.getCTP().getPPr().getNumPr();
        String level = numbering.isSetIlvl() ? numbering.getIlvl().getVal().toString() : "0";
        return numbering.getNumId().getVal() + ":" + level;
    }

    private String mergedEnumerationText(List<XWPFParagraph> paragraphs) {
        StringBuilder merged = new StringBuilder();
        for (int index = 0; index < paragraphs.size(); index++) {
            appendEnumerationItem(merged, DayaEnumerationRules.mergedItemText(
                    index + 1, normalize(paragraphs.get(index).getText())));
        }
        return merged.toString();
    }

    private void appendEnumerationItem(StringBuilder merged, String item) {
        if (!merged.isEmpty() && "。！？!?；;".indexOf(merged.charAt(merged.length() - 1)) < 0) {
            merged.append('。');
        }
        merged.append(item);
    }

    private boolean isHeadingStyle(XWPFParagraph paragraph) {
        String style = normalize(paragraph.getStyle()).toLowerCase(Locale.ROOT);
        return style.contains("heading") || style.contains("title") || style.contains("标题")
                || style.matches("[1-9]");
    }

    private boolean isCatalogStyle(XWPFParagraph paragraph) {
        String style = normalize(paragraph.getStyle()).toLowerCase(Locale.ROOT);
        return style.startsWith("toc") || style.contains("目录");
    }

    private boolean isTechnicalFragment(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (containsAny(text, "@RestController", "@Controller", "@Service", "@Component", "@Mapper",
                "@RequestMapping", "@GetMapping", "@PostMapping", "public class", "private ", "protected ")) {
            return true;
        }
        if (containsAny(lower, "<template", "</template>", "<script", "</script>", "select ", "insert ",
                "update ", "delete ", "create table", "alter table", "export default", "=>", "{", "}")) {
            return true;
        }
        long punctuation = text.chars().filter(ch -> "{}();=<>/\\".indexOf(ch) >= 0).count();
        return punctuation >= 5 && punctuation * 8 > text.length();
    }

    /**
     * Word commonly places a bookmark start before abstract text. It carries no visible text, so
     * Daya may keep that leading marker while still rejecting bookmark ends, hyperlinks,
     * fields, drawings, manual breaks and other structures whose range could be changed by rewrite.
     */
    private boolean hasOnlyDayaTextRuns(XWPFParagraph paragraph) {
        return hasOnlyTextRuns(paragraph, true);
    }

    private boolean hasOnlyTextRuns(XWPFParagraph paragraph, boolean allowTextBoundaries) {
        Node paragraphNode = paragraph.getCTP().getDomNode();
        boolean textRunSeen = false;
        for (Node child = paragraphNode.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            String name = child.getLocalName();
            if ("pPr".equals(name)) continue;
            if (allowTextBoundaries && !textRunSeen && "bookmarkStart".equals(name)) {
                continue;
            }
            if (!"r".equals(name)) return false;
            textRunSeen = true;
            for (Node runChild = child.getFirstChild(); runChild != null; runChild = runChild.getNextSibling()) {
                if (runChild.getNodeType() != Node.ELEMENT_NODE) continue;
                String runName = runChild.getLocalName();
                if (!"rPr".equals(runName) && !"t".equals(runName)
                        && !"lastRenderedPageBreak".equals(runName)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isCatalogTitle(String text) {
        return text.replaceAll("\\s+", "").equalsIgnoreCase("目录");
    }

    private boolean isAbstractTitle(String text) {
        String compact = text.replaceAll("\\s+", "");
        return compact.equals("摘要") || compact.equalsIgnoreCase("abstract");
    }

    private boolean isChineseAbstractTitle(String text) {
        return text.replaceAll("\\s+", "").equals("摘要");
    }

    private boolean isEnglishAbstractTitle(String text) {
        return text.replaceAll("\\s+", "").equalsIgnoreCase("abstract");
    }

    private boolean isKeywordLine(String text) {
        String compact = text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return compact.startsWith("关键词") || compact.startsWith("关键字") || compact.startsWith("keywords");
    }

    private boolean isTrailingSection(String text) {
        String compact = text.replaceAll("\\s+", "");
        return compact.matches("^(参考文献|致谢|附录(?:[A-Z一二三四五六七八九十])?|声明|原创性声明|学位论文原创性声明).*$");
    }

    private boolean isReferenceTitle(String text) {
        return text.replaceAll("\\s+", "").matches("^参考文献.*$");
    }

    private boolean isAcknowledgementsTitle(String text) {
        return text.replaceAll("\\s+", "").equals("致谢");
    }

    private boolean isProtectedTrailingTitle(String compactText) {
        return compactText.matches("^(附录(?:[A-Z一二三四五六七八九十])?|声明|原创性声明|学位论文原创性声明).*$");
    }

    private List<Batch> batches(List<Target> targets) {
        List<Batch> result = new ArrayList<>();
        List<Target> current = new ArrayList<>();
        int characters = 0;
        for (Target target : targets) {
            boolean overflow = !current.isEmpty()
                    && (current.size() >= DAYA_MAX_BATCH_PARAGRAPHS
                    || characters + target.originalText().length() > DAYA_MAX_BATCH_CHARACTERS
                    || !current.get(0).context().equals(target.context()));
            if (overflow) {
                result.add(new Batch(List.copyOf(current)));
                current.clear();
                characters = 0;
            }
            current.add(target);
            characters += target.originalText().length();
        }
        if (!current.isEmpty()) result.add(new Batch(List.copyOf(current)));
        return result;
    }

    private void validateCandidate(String original, String rewritten) {
        int originalLength = original.length();
        int rewrittenLength = rewritten.length();
        boolean dayaEnumeration = DayaEnumerationRules.requiresBreak(original);
        double maximumRatio = 1.15;
        int minimumLength = dayaEnumeration
                ? Math.max(8, DayaEnumerationRules.itemCount(original) * 4)
                : Math.max(12, (int) Math.floor(originalLength * 0.70));
        if (rewrittenLength < minimumLength
                || rewrittenLength > Math.ceil(originalLength * maximumRatio)) {
            throw new IllegalStateException(dayaEnumeration
                    ? "大雅列举段未满足每项短句的基础长度或超过 115% 保护范围"
                    : "大雅普通段落长度超出 70%-115% 保护范围");
        }
        if (rewritten.startsWith("```") || rewritten.contains("以下是改写") || rewritten.contains("改写结果：")) {
            throw new IllegalStateException("改写段落包含模型说明文字");
        }
        if (dayaEnumeration) DayaEnumerationRules.validateRewrite(original, rewritten);
    }

    private StyledParagraph styledParagraphFor(Target target, AtomicInteger sequence) {
        if (target.preparation() != DayaPreparation.MERGED_LIST) {
            StyledParagraph styled = protectStyledRuns(
                    target.paragraph(), normalize(target.paragraph().getText()), sequence);
            if (target.preparation() == DayaPreparation.INLINE_NUMERIC) {
                return new StyledParagraph(
                        DayaEnumerationRules.normalizeInlineNumericEnumeration(styled.modelText()),
                        styled.fragments());
            }
            return styled;
        }

        StringBuilder modelText = new StringBuilder();
        Map<String, StyledFragment> fragments = new LinkedHashMap<>();
        for (int index = 0; index < target.sourceParagraphs().size(); index++) {
            XWPFParagraph paragraph = target.sourceParagraphs().get(index);
            StyledParagraph styled = protectStyledRuns(
                    paragraph, normalize(paragraph.getText()), sequence);
            appendEnumerationItem(modelText, DayaEnumerationRules.mergedItemText(
                    index + 1, styled.modelText()));
            fragments.putAll(styled.fragments());
        }
        return new StyledParagraph(modelText.toString(),
                java.util.Collections.unmodifiableMap(new LinkedHashMap<>(fragments)));
    }

    private void removeMergedListStructure(Target target) {
        if (target.preparation() != DayaPreparation.MERGED_LIST) return;
        XWPFParagraph first = target.paragraph();
        if (first.getCTP().isSetPPr() && first.getCTP().getPPr().isSetNumPr()) {
            first.getCTP().getPPr().unsetNumPr();
        }
        for (int index = 1; index < target.sourceParagraphs().size(); index++) {
            Node paragraphNode = target.sourceParagraphs().get(index).getCTP().getDomNode();
            Node parent = paragraphNode.getParentNode();
            if (parent != null) parent.removeChild(paragraphNode);
        }
    }

    private StyledParagraph protectStyledRuns(XWPFParagraph paragraph, String originalText,
                                              AtomicInteger sequence) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.size() < 2) return StyledParagraph.plain(originalText);
        XWPFRun baseRun = baseRun(runs);
        String baseSignature = runPropertiesSignature(baseRun);
        StringBuilder modelText = new StringBuilder();
        Map<String, StyledFragment> fragments = new LinkedHashMap<>();
        for (XWPFRun run : runs) {
            String text = run.text();
            if (text == null || text.isEmpty()) continue;
            if (runPropertiesSignature(run).equals(baseSignature)) {
                modelText.append(text);
                continue;
            }
            String token = "[[DROP_STYLE_PROTECTED_" + sequence.getAndIncrement() + "]]";
            CTRPr properties = run.getCTR().isSetRPr()
                    ? (CTRPr) run.getCTR().getRPr().copy()
                    : null;
            fragments.put(token, new StyledFragment(text, properties));
            modelText.append(token);
        }
        if (fragments.isEmpty()) return StyledParagraph.plain(originalText);
        String normalized = normalize(modelText.toString());
        return new StyledParagraph(normalized,
                java.util.Collections.unmodifiableMap(new LinkedHashMap<>(fragments)));
    }

    private String runPropertiesSignature(XWPFRun run) {
        return run.getCTR().isSetRPr() ? run.getCTR().getRPr().xmlText() : "";
    }

    private XWPFRun baseRun(List<XWPFRun> runs) {
        return runs.stream()
                .filter(run -> run.text() != null && !run.text().isEmpty())
                .findFirst()
                .orElse(runs.get(0));
    }

    private void replaceParagraphText(XWPFParagraph paragraph, StyledRestore restored) {
        XWPFRun templateRun = paragraph.getRuns().isEmpty() ? null : baseRun(paragraph.getRuns());
        CTRPr runProperties = null;
        if (templateRun != null && templateRun.getCTR().isSetRPr()) {
            runProperties = (CTRPr) templateRun.getCTR().getRPr().copy();
        }
        for (int index = paragraph.getRuns().size() - 1; index >= 0; index--) paragraph.removeRun(index);
        for (RunPiece piece : restored.pieces()) {
            if (piece.text().isEmpty()) continue;
            XWPFRun run = paragraph.createRun();
            CTRPr properties = piece.styled() ? piece.properties() : runProperties;
            if (properties != null) run.getCTR().setRPr((CTRPr) properties.copy());
            run.setText(piece.text());
        }
    }

    private void writeAtomically(XWPFDocument document, Path output) throws Exception {
        Files.createDirectories(output.toAbsolutePath().getParent());
        Path temporary = output.resolveSibling(output.getFileName() + "." + UUID.randomUUID() + ".part");
        try {
            try (OutputStream stream = Files.newOutputStream(temporary)) {
                document.write(stream);
            }
            if (Files.size(temporary) <= 0) throw new IllegalStateException("生成的 DOCX 文件为空");
            try {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private int countCjk(String text) {
        return (int) text.codePoints().filter(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN).count();
    }

    private int countLatinLetters(String text) {
        return (int) text.codePoints()
                .filter(codePoint -> Character.UnicodeScript.of(codePoint)
                        == Character.UnicodeScript.LATIN)
                .filter(Character::isLetter)
                .count();
    }

    private boolean containsAny(String text, String... fragments) {
        for (String fragment : fragments) if (text.contains(fragment)) return true;
        return false;
    }

    private String normalize(String text) {
        return text == null ? "" : text.replace('\u00A0', ' ').replaceAll("[\\t\\r ]+", " ").trim();
    }

    private static String compact(String message) {
        if (message == null || message.isBlank()) return "无详细信息";
        String value = message.replaceAll("\\s+", " ").trim();
        return value.length() > 180 ? value.substring(0, 180) + "..." : value;
    }

    record Target(String id, int index, XWPFParagraph paragraph,
                  String originalText, String context,
                  List<XWPFParagraph> sourceParagraphs,
                  DayaPreparation preparation) {
        Target(String id, int index, XWPFParagraph paragraph,
               String originalText, String context) {
            this(id, index, paragraph, originalText, context,
                    List.of(paragraph), DayaPreparation.NONE);
        }

        Target {
            sourceParagraphs = sourceParagraphs == null || sourceParagraphs.isEmpty()
                    ? List.of(paragraph) : List.copyOf(sourceParagraphs);
            preparation = preparation == null ? DayaPreparation.NONE : preparation;
        }
    }

    private record Batch(List<Target> targets) { }

    private enum DayaSection {
        INCLUDE,
        SKIP
    }

    enum DayaPreparation {
        NONE,
        INLINE_NUMERIC,
        MERGED_LIST
    }

    private record StyledFragment(String text, CTRPr properties) { }

    private record RunPiece(String text, boolean styled, CTRPr properties) { }

    private record StyledRestore(String text, List<RunPiece> pieces) { }

    private record StyledParagraph(String modelText, Map<String, StyledFragment> fragments) {
        private static StyledParagraph plain(String text) {
            return new StyledParagraph(text, Map.of());
        }

        private StyledRestore restore(String rewritten) {
            String value = rewritten == null ? "" : rewritten.trim();
            if (value.isBlank()) throw new IllegalStateException("平台 Skill 返回了空段落");
            if (fragments.isEmpty()) {
                return new StyledRestore(value, List.of(new RunPiece(value, false, null)));
            }
            List<RunPiece> pieces = new ArrayList<>();
            StringBuilder restored = new StringBuilder();
            int cursor = 0;
            for (Map.Entry<String, StyledFragment> entry : fragments.entrySet()) {
                String token = entry.getKey();
                int index = value.indexOf(token, cursor);
                if (index < cursor || occurrences(value, token) != 1) {
                    throw new IllegalStateException("平台 Skill 未完整保留局部格式占位符");
                }
                if (index > cursor) {
                    String plain = value.substring(cursor, index);
                    pieces.add(new RunPiece(plain, false, null));
                    restored.append(plain);
                }
                StyledFragment fragment = entry.getValue();
                pieces.add(new RunPiece(fragment.text(), true, fragment.properties()));
                restored.append(fragment.text());
                cursor = index + token.length();
            }
            if (cursor < value.length()) {
                String plain = value.substring(cursor);
                pieces.add(new RunPiece(plain, false, null));
                restored.append(plain);
            }
            return new StyledRestore(restored.toString(), List.copyOf(pieces));
        }

        private static int occurrences(String value, String token) {
            int count = 0;
            int cursor = 0;
            while ((cursor = value.indexOf(token, cursor)) >= 0) {
                count++;
                cursor += token.length();
            }
            return count;
        }
    }

    public record ProcessingResult(int totalParagraphs, int processedParagraphs,
                                   int rewrittenParagraphs, int failedParagraphs,
                                   List<String> failureMessages) { }

    @FunctionalInterface
    public interface ProgressListener {
        ProgressListener NOOP = (total, processed, rewritten, message) -> { };

        void update(int total, int processed, int rewritten, String message);
    }
}
