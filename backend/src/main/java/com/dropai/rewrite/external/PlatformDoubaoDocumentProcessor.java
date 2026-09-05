package com.dropai.rewrite.external;

import jakarta.annotation.PreDestroy;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.TableRowHeightRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
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
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DOCX processor used only by the opt-in Daya profile.
 * Media, headings and trailing reference sections are never submitted. Daya has a conservative,
 * separately guarded path for narrative body-table cells.
 */
@Service
public class PlatformDoubaoDocumentProcessor {
    static final int DAYA_MAX_BATCH_PARAGRAPHS = 4;
    static final int DAYA_MAX_BATCH_CHARACTERS = 1800;
    static final int DAYA_MAX_CONCURRENCY = 32;
    private static final int MIN_PARAGRAPH_CHARACTERS = 8;
    private static final int DAYA_TABLE_MIN_CHARACTERS = 18;
    private static final int DAYA_TABLE_MIN_CJK = 8;
    private static final Pattern BODY_HEADING = Pattern.compile(
            "^(?:引言|绪论|前言|正文|Introduction|第[一二三四五六七八九十百]+章.*"
                    + "|[一二三四五六七八九十]+、.{1,80}"
                    + "|[1-9][0-9]?(?:[.．][0-9]+){0,3}[、.．\\s]+.{1,80})$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_NUMBERED_HEADING = Pattern.compile(
            "^(?:第[一二三四五六七八九十百]+[章节篇].*|[一二三四五六七八九十]+、.{1,80}|[0-9]+(?:[.．][0-9]+){0,4}[、.．\\s]+.{1,100})$");
    private static final Pattern CATALOG_LINE = Pattern.compile("^.+[.·•…]{2,}\\s*[0-9０-９]+\\s*$");
    private static final Pattern MANUAL_CATALOG_LINE = Pattern.compile(
            "^.+(?:[.·•…]{2,}|\\s+)\\s*[0-9０-９]+\\s*$");
    private static final Pattern REFERENCE_LINE = Pattern.compile("^\\s*\\[[0-9０-９]+].*$");
    private static final Pattern CAPTION_LINE = Pattern.compile(
            "^(?:图|表|公式)\\s*[0-9０-９]+(?:[.．\\-—][0-9０-９]+)*(?:\\s+.*)?$");
    private static final String TRAILING_NUMBER_PREFIX =
            "(?:第(?:[一二三四五六七八九十百]+|[0-9０-９]+)章"
                    + "|[0-9０-９]+(?:[.．][0-9０-９]+)*[、.．]?)?";
    private static final Pattern DAYA_TABLE_INVARIANT = Pattern.compile(
            "(?i)(“[^”\\r\\n]{1,30}”|"
                    + "[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]+(?:类|级|项)?|"
                    + "(?:图|表|公式)\\s*[0-9０-９]+(?:[.．\\-—][0-9０-９]+)*|"
                    + "\\[[0-9０-９,，;；\\-–—~～\\s]+]|"
                    + "[A-Za-z][A-Za-z0-9_.:/\\-]*|"
                    + "[0-9０-９]+(?:[.．][0-9０-９]+)*(?:%|％|亿元|万元|元|万|公顷|平方公里|公里|平方米|米|家|处|项|人|年|月|日|分|级|类|ms|s|kg|g|mm|cm|m|KB|MB|GB|℃)?|"
                    + "尚未|不代表|不作为|不足|缺少|缺乏|未|无)");

    private final PlatformDoubaoRewriteGateway gateway;
    private final PlatformDocumentTextProtector protector;
    private final ExecutorService dayaBatchExecutor;

    public PlatformDoubaoDocumentProcessor(PlatformDoubaoRewriteGateway gateway,
                                           PlatformDocumentTextProtector protector) {
        this.gateway = gateway;
        this.protector = protector;
        this.dayaBatchExecutor = Executors.newFixedThreadPool(
                DAYA_MAX_CONCURRENCY, dayaThreadFactory());
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
            DayaTableStructureSnapshot tableSnapshot = DayaTableStructureSnapshot.capture(document);
            if (targets.isEmpty()) {
                throw new IllegalArgumentException("未识别到可处理的正文自然语言段落");
            }
            progress.update(targets.size(), 0, 0, "已收集全文可处理段落，正在加载平台 Skill");

            List<Batch> batches = batches(targets);
            AtomicInteger tokenSequence = new AtomicInteger();
            AtomicInteger styleSequence = new AtomicInteger();
            List<PreparedBatch> preparedBatches = new ArrayList<>();
            for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
                preparedBatches.add(prepareBatch(
                        batchIndex, batches.get(batchIndex), tokenSequence, styleSequence));
            }

            BatchResult[] completedBatches = rewriteBatchesConcurrently(
                    preparedBatches, targets.size(), platform, mode, progress);
            int processed = 0;
            int rewritten = 0;
            int failed = 0;
            List<String> failureMessages = new ArrayList<>();
            List<ParagraphRewrite> acceptedRewrites = new ArrayList<>();
            for (BatchResult batchResult : completedBatches) {
                processed += batchResult.targetCount();
                rewritten += batchResult.rewrites().size();
                failed += batchResult.failed();
                for (String message : batchResult.failureMessages()) {
                    if (failureMessages.size() >= 3) break;
                    failureMessages.add(message);
                }
                acceptedRewrites.addAll(batchResult.rewrites());
            }

            int incomplete = Math.max(failed, processed - rewritten);
            if (failed > 0 || rewritten != processed) {
                throw new IllegalStateException("大雅全文改写尚有 " + incomplete
                        + " 段未生成可用结果；为避免夹带未改原文，本次不生成部分文档"
                        + (failureMessages.isEmpty() ? "" : "：" + String.join("；", failureMessages)));
            }
            for (ParagraphRewrite rewrite : acceptedRewrites) {
                replaceParagraphText(rewrite.target().paragraph(), rewrite.restored());
                removeMergedListStructure(rewrite.target());
            }
            tableSnapshot.validate(document);
            writeAtomically(document, output, tableSnapshot);
            return new ProcessingResult(targets.size(), processed, rewritten, failed, List.copyOf(failureMessages));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("平台 DOCX 处理失败：" + compact(exception.getMessage()), exception);
        }
    }

    private PreparedBatch prepareBatch(int index, Batch batch,
                                       AtomicInteger tokenSequence,
                                       AtomicInteger styleSequence) {
        Map<String, PlatformDocumentTextProtector.ProtectedText> protectedById = new LinkedHashMap<>();
        Map<String, StyledParagraph> styledById = new LinkedHashMap<>();
        List<PlatformDoubaoRewriteGateway.Segment> segments = new ArrayList<>();
        for (Target target : batch.targets()) {
            StyledParagraph styledParagraph = styledParagraphFor(target, styleSequence);
            PlatformDocumentTextProtector.ProtectedText protectedText =
                    "英文摘要".equals(target.context())
                            ? protector.protectDayaEnglishProse(
                                    styledParagraph.modelText(), tokenSequence)
                            : protector.protect(styledParagraph.modelText(), tokenSequence);
            styledById.put(target.id(), styledParagraph);
            protectedById.put(target.id(), protectedText);
            segments.add(new PlatformDoubaoRewriteGateway.Segment(
                    target.id(), protectedText.text(), target.context()));
        }
        return new PreparedBatch(index, batch, Map.copyOf(protectedById),
                Map.copyOf(styledById), List.copyOf(segments));
    }

    private BatchResult[] rewriteBatchesConcurrently(
            List<PreparedBatch> batches, int totalTargets,
            XuejiePlatform platform, XuejieRewriteMode mode,
            ProgressListener progress) {
        CompletionService<BatchResult> completionService =
                new ExecutorCompletionService<>(dayaBatchExecutor);
        List<Future<BatchResult>> futures = new ArrayList<>();
        int submitted = Math.min(DAYA_MAX_CONCURRENCY, batches.size());
        for (int index = 0; index < submitted; index++) {
            PreparedBatch batch = batches.get(index);
            futures.add(completionService.submit(
                    () -> rewritePreparedBatch(batch, platform, mode)));
        }

        BatchResult[] results = new BatchResult[batches.size()];
        int processed = 0;
        int rewritten = 0;
        try {
            for (int completed = 0; completed < batches.size(); completed++) {
                BatchResult result = completedBatch(completionService);
                results[result.index()] = result;
                processed += result.targetCount();
                rewritten += result.rewrites().size();
                if (submitted < batches.size()) {
                    PreparedBatch next = batches.get(submitted++);
                    futures.add(completionService.submit(
                            () -> rewritePreparedBatch(next, platform, mode)));
                }
                progress.update(totalTargets, processed, rewritten,
                        platform.remoteName() + " Skill 32 路并发处理中："
                                + processed + "/" + totalTargets + " 段");
            }
        } catch (RuntimeException | Error failure) {
            futures.forEach(future -> future.cancel(true));
            throw failure;
        }
        return retryFailedTargetsConcurrently(results, totalTargets, platform, mode, progress);
    }

    /**
     * A failed first draft is retried once as an isolated segment.  The first-wave batch tasks
     * have all completed before this phase starts, so the same fixed 32-thread executor keeps the
     * remote-call ceiling unchanged while allowing unrelated failed paragraphs to retry in
     * parallel.  Successful first-wave targets are never submitted again.
     */
    private BatchResult[] retryFailedTargetsConcurrently(
            BatchResult[] initialResults, int totalTargets,
            XuejiePlatform platform, XuejieRewriteMode mode,
            ProgressListener progress) {
        List<RetryTarget> retryTargets = new ArrayList<>();
        int initialRewrites = 0;
        for (BatchResult result : initialResults) {
            initialRewrites += result.rewrites().size();
            retryTargets.addAll(result.retryTargets());
        }
        if (retryTargets.isEmpty()) return initialResults;

        CompletionService<RetryResult> completionService =
                new ExecutorCompletionService<>(dayaBatchExecutor);
        List<Future<RetryResult>> futures = new ArrayList<>();
        int submitted = Math.min(DAYA_MAX_CONCURRENCY, retryTargets.size());
        for (int index = 0; index < submitted; index++) {
            RetryTarget retryTarget = retryTargets.get(index);
            futures.add(completionService.submit(
                    () -> retryFailedTarget(retryTarget, platform, mode)));
        }

        Map<Integer, List<RetryResult>> resultsByBatch = new LinkedHashMap<>();
        int retryRewrites = 0;
        try {
            for (int completed = 0; completed < retryTargets.size(); completed++) {
                RetryResult result = completedRetry(completionService);
                resultsByBatch.computeIfAbsent(result.batchIndex(), ignored -> new ArrayList<>())
                        .add(result);
                if (result.rewrite() != null) retryRewrites++;
                if (submitted < retryTargets.size()) {
                    RetryTarget next = retryTargets.get(submitted++);
                    futures.add(completionService.submit(
                            () -> retryFailedTarget(next, platform, mode)));
                }
                progress.update(totalTargets, totalTargets, initialRewrites + retryRewrites,
                        platform.remoteName() + " Skill 失败段单段重试（32 路）："
                                + (completed + 1) + "/" + retryTargets.size() + " 段");
            }
        } catch (RuntimeException | Error failure) {
            futures.forEach(future -> future.cancel(true));
            throw failure;
        }

        BatchResult[] merged = new BatchResult[initialResults.length];
        for (BatchResult initial : initialResults) {
            List<ParagraphRewrite> rewrites = new ArrayList<>(initial.rewrites());
            List<String> failures = new ArrayList<>();
            int failed = 0;
            for (RetryResult retry : resultsByBatch.getOrDefault(initial.index(), List.of())) {
                if (retry.failure() == null) {
                    if (retry.rewrite() != null) rewrites.add(retry.rewrite());
                } else {
                    failed++;
                    if (failures.size() < 3) failures.add(retry.failure());
                }
            }
            merged[initial.index()] = new BatchResult(initial.index(), initial.targetCount(),
                    List.copyOf(rewrites), failed, List.copyOf(failures), List.of());
        }
        return merged;
    }

    private BatchResult completedBatch(CompletionService<BatchResult> completionService) {
        try {
            Future<BatchResult> future = completionService.take();
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("大雅并发处理被中断", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("大雅并发处理失败", cause);
        }
    }

    private RetryResult completedRetry(CompletionService<RetryResult> completionService) {
        try {
            Future<RetryResult> future = completionService.take();
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("大雅失败段单段重试被中断", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("大雅失败段单段重试失败", cause);
        }
    }

    private BatchResult rewritePreparedBatch(PreparedBatch prepared,
                                             XuejiePlatform platform,
                                             XuejieRewriteMode mode) {
        Batch batch = prepared.batch();
        List<ParagraphRewrite> rewrites = new ArrayList<>();
        List<RetryTarget> retryTargets = new ArrayList<>();
        try {
            Map<String, String> responses = gateway.rewriteBatch(
                    prepared.segments(), platform, mode);
            for (Target target : batch.targets()) {
                RewriteAttempt attempt = validateResponse(prepared, target, responses.get(target.id()));
                if (attempt.failure() == null) {
                    if (attempt.rewrite() != null) rewrites.add(attempt.rewrite());
                } else {
                    retryTargets.add(retryTarget(prepared, target, attempt.failure()));
                }
            }
        } catch (RuntimeException batchFailure) {
            String failure = compact(batchFailure.getMessage());
            for (Target target : batch.targets()) {
                retryTargets.add(retryTarget(prepared, target, failure));
            }
        }
        return new BatchResult(prepared.index(), batch.targets().size(),
                List.copyOf(rewrites), retryTargets.size(), List.of(), List.copyOf(retryTargets));
    }

    private RetryResult retryFailedTarget(RetryTarget retryTarget,
                                          XuejiePlatform platform,
                                          XuejieRewriteMode mode) {
        try {
            Map<String, String> response = gateway.rewriteBatch(
                    List.of(retryTarget.segment()), platform, mode);
            RewriteAttempt attempt = validateResponse(
                    retryTarget.prepared(), retryTarget.target(),
                    response.get(retryTarget.target().id()));
            if (attempt.failure() == null) {
                return new RetryResult(retryTarget.batchIndex(), attempt.rewrite(), null);
            }
            return new RetryResult(retryTarget.batchIndex(), null,
                    compact("首轮：" + retryTarget.firstFailure()
                            + "；单段重试：" + attempt.failure()));
        } catch (RuntimeException retryFailure) {
            return new RetryResult(retryTarget.batchIndex(), null,
                    compact("首轮：" + retryTarget.firstFailure()
                            + "；单段重试：" + compact(retryFailure.getMessage())));
        }
    }

    private RewriteAttempt validateResponse(PreparedBatch prepared, Target target, String response) {
        try {
            String protectedStylesRestored = prepared.protectedById().get(target.id())
                    .validateAndRestore(response);
            StyledRestore restored = prepared.styledById().get(target.id())
                    .restore(protectedStylesRestored);
            validateCandidate(target, restored.text());
            return new RewriteAttempt(new ParagraphRewrite(target, restored), null);
        } catch (RuntimeException validationFailure) {
            return new RewriteAttempt(null, compact(validationFailure.getMessage()));
        }
    }

    private RetryTarget retryTarget(PreparedBatch prepared, Target target, String firstFailure) {
        PlatformDoubaoRewriteGateway.Segment segment = prepared.segments().stream()
                .filter(candidate -> candidate.id().equals(target.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到大雅失败段保护文本"));
        return new RetryTarget(prepared.index(), prepared, target, segment, firstFailure);
    }

    @PreDestroy
    void shutdownDayaBatchExecutor() {
        dayaBatchExecutor.shutdownNow();
    }

    private ThreadFactory dayaThreadFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable,
                    "daya-document-batch-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
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
        boolean hasAbstractBoundary = paragraphs.stream()
                .map(XWPFParagraph::getText)
                .map(this::normalize)
                .anyMatch(this::isAbstractTitle);
        boolean hasCatalogBoundary = paragraphs.stream()
                .map(XWPFParagraph::getText)
                .map(this::normalize)
                .anyMatch(this::isCatalogTitle);
        boolean boundaryReached = !hasAbstractBoundary && !hasCatalogBoundary;
        boolean catalogActive = false;
        boolean bodyStarted = false;

        for (int index = 0; index < paragraphs.size(); index++) {
            XWPFParagraph paragraph = paragraphs.get(index);
            String text = normalize(paragraph.getText());
            String compactText = text.replaceAll("\\s+", "");

            if (isChineseAbstractTitle(text)) {
                boundaryReached = true;
                catalogActive = false;
                section = DayaSection.INCLUDE;
                context = "中文摘要";
                continue;
            }
            if (isEnglishAbstractTitle(text)) {
                boundaryReached = true;
                catalogActive = false;
                section = DayaSection.INCLUDE;
                context = "英文摘要";
                continue;
            }
            if (isCatalogTitle(text)) {
                boundaryReached = true;
                catalogActive = true;
                section = DayaSection.SKIP;
                continue;
            }
            if (!boundaryReached) continue;
            if (isKeywordLine(text)) {
                section = DayaSection.SKIP;
                continue;
            }
            if (isReferenceTitle(text)) {
                if (catalogActive || isCatalogStyle(paragraph)) continue;
                if (bodyStarted) break;
                section = DayaSection.SKIP;
                continue;
            }
            if (isAcknowledgementsTitle(text) || isProtectedTrailingTitle(compactText)) {
                if (catalogActive || isCatalogStyle(paragraph)) continue;
                if (bodyStarted) break;
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
            if (catalogActive
                    && (isCatalogStyle(paragraph)
                    || (!isHeadingStyle(paragraph) && MANUAL_CATALOG_LINE.matcher(text).matches()))) {
                continue;
            }
            boolean longLeadingListItem = bodyStarted && text.length() > 30
                    && DayaEnumerationRules.isLeadingListItem(text);
            if (!longLeadingListItem
                    && isDayaBodyStartTitle(paragraph, text) && !isCatalogStyle(paragraph)) {
                bodyStarted = true;
                catalogActive = false;
                section = DayaSection.INCLUDE;
                context = text;
                continue;
            }
            if (catalogActive) continue;
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
        targets.addAll(collectDayaTableTargets(document));
        return targets;
    }

    private List<Target> collectDayaTableTargets(XWPFDocument document) {
        List<Target> targets = new ArrayList<>();
        DayaSection section = DayaSection.SKIP;
        String context = "正文";
        String caption = "";
        boolean hasAbstractBoundary = document.getParagraphs().stream()
                .map(XWPFParagraph::getText).map(this::normalize).anyMatch(this::isAbstractTitle);
        boolean hasCatalogBoundary = document.getParagraphs().stream()
                .map(XWPFParagraph::getText).map(this::normalize).anyMatch(this::isCatalogTitle);
        boolean boundaryReached = !hasAbstractBoundary && !hasCatalogBoundary;
        boolean catalogActive = false;
        boolean bodyStarted = false;
        int tableIndex = 0;

        for (var element : document.getBodyElements()) {
            if (element instanceof XWPFParagraph paragraph) {
                String text = normalize(paragraph.getText());
                String compactText = text.replaceAll("\\s+", "");
                if (isChineseAbstractTitle(text) || isEnglishAbstractTitle(text)) {
                    boundaryReached = true;
                    catalogActive = false;
                    section = DayaSection.INCLUDE;
                    context = isChineseAbstractTitle(text) ? "中文摘要" : "英文摘要";
                    caption = "";
                    continue;
                }
                if (isCatalogTitle(text)) {
                    boundaryReached = true;
                    catalogActive = true;
                    section = DayaSection.SKIP;
                    caption = "";
                    continue;
                }
                if (!boundaryReached) continue;
                if (isKeywordLine(text)) {
                    section = DayaSection.SKIP;
                    caption = "";
                    continue;
                }
                if (isReferenceTitle(text)) {
                    if (catalogActive || isCatalogStyle(paragraph)) continue;
                    if (bodyStarted) break;
                    section = DayaSection.SKIP;
                    continue;
                }
                if (isAcknowledgementsTitle(text) || isProtectedTrailingTitle(compactText)) {
                    if (catalogActive || isCatalogStyle(paragraph)) continue;
                    if (bodyStarted) break;
                    section = DayaSection.SKIP;
                    continue;
                }
                if (catalogActive && (isCatalogStyle(paragraph)
                        || (!isHeadingStyle(paragraph) && MANUAL_CATALOG_LINE.matcher(text).matches()))) {
                    continue;
                }
                if (isDayaBodyStartTitle(paragraph, text) && !isCatalogStyle(paragraph)) {
                    bodyStarted = true;
                    catalogActive = false;
                    section = DayaSection.INCLUDE;
                    context = text;
                    caption = "";
                    continue;
                }
                if (catalogActive) continue;
                if (bodyStarted && isHeadingStyle(paragraph)) {
                    context = text.isBlank() ? context : text;
                    caption = "";
                    continue;
                }
                if (CAPTION_LINE.matcher(text).matches()) caption = text;
                continue;
            }
            if (!(element instanceof XWPFTable table)) continue;
            if (bodyStarted && section == DayaSection.INCLUDE) {
                String tableContext = context + " / "
                        + (caption.isBlank() ? "表格长说明" : caption + "（表格长说明）");
                targets.addAll(collectDayaTableTargets(table, tableIndex, tableContext));
            }
            tableIndex++;
            caption = "";
        }
        return targets;
    }

    private List<Target> collectDayaTableTargets(XWPFTable table, int tableIndex, String context) {
        List<Target> targets = new ArrayList<>();
        List<XWPFTableRow> rows = table.getRows();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            XWPFTableRow row = rows.get(rowIndex);
            if (hasExactRowHeight(row)
                    || (row.getCtRow().isSetTrPr()
                    && row.getCtRow().getTrPr().sizeOfTblHeaderArray() > 0)) {
                continue;
            }
            List<XWPFTableCell> cells = row.getTableCells();
            for (int cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
                XWPFTableCell cell = cells.get(cellIndex);
                if (!isDayaNarrativeTableCell(cell)) continue;
                XWPFParagraph paragraph = cell.getParagraphs().get(0);
                String text = normalize(paragraph.getText());
                targets.add(new Target("t" + tableIndex + "r" + rowIndex + "c" + cellIndex,
                        -1, paragraph, text, context, List.of(paragraph), DayaPreparation.TABLE_TEXT));
            }
        }
        return targets;
    }

    /**
     * Some Word documents contain a {@code w:trHeight} element without {@code w:hRule}.
     * POI's {@link XWPFTableRow#getHeightRule()} dereferences that missing enum value and throws,
     * so inspect the underlying XML defensively instead.
     */
    private boolean hasExactRowHeight(XWPFTableRow row) {
        if (row == null || !row.getCtRow().isSetTrPr()) return false;
        var properties = row.getCtRow().getTrPr();
        for (int index = 0; index < properties.sizeOfTrHeightArray(); index++) {
            var heightRule = properties.getTrHeightArray(index).getHRule();
            if (heightRule != null
                    && heightRule.intValue() == TableRowHeightRule.EXACT.getValue()) {
                return true;
            }
        }
        return false;
    }

    private boolean isDayaNarrativeTableCell(XWPFTableCell cell) {
        if (!cell.getTables().isEmpty() || cell.getParagraphs().size() != 1) return false;
        var properties = cell.getCTTc().getTcPr();
        if (properties != null && (properties.isSetHMerge() || properties.isSetVMerge()
                || (properties.isSetGridSpan() && properties.getGridSpan().getVal() != null
                && properties.getGridSpan().getVal().intValue() > 1))) {
            return false;
        }
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        String text = normalize(paragraph.getText());
        if (hasAutomaticNumbering(paragraph)
                || !hasOnlyDayaTableTextRuns(paragraph)) {
            return false;
        }
        if (text.length() < DAYA_TABLE_MIN_CHARACTERS || countCjk(text) < DAYA_TABLE_MIN_CJK) return false;
        if (!containsAny(text, "，", "。", "；", "：", "！", "？", "、")) return false;
        if (isTechnicalFragment(text)) return false;
        int visible = (int) text.codePoints().filter(codePoint -> !Character.isWhitespace(codePoint)).count();
        int technical = (int) text.codePoints().filter(this::isDayaTableTechnicalCharacter).count();
        return technical * 5 <= Math.max(1, visible) * 2;
    }

    private boolean isDayaTableTechnicalCharacter(int codePoint) {
        if (Character.isDigit(codePoint)) return true;
        if (Character.isLetter(codePoint)
                && Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN) return true;
        return ".%％/\\+-=—–_".indexOf(codePoint) >= 0;
    }

    private boolean isDayaBodyStartTitle(XWPFParagraph paragraph, String text) {
        if (BODY_HEADING.matcher(text).matches()) return true;
        return isHeadingStyle(paragraph)
                && !isAbstractTitle(text)
                && !isCatalogTitle(text)
                && !isReferenceTitle(text)
                && !isAcknowledgementsTitle(text)
                && !isProtectedTrailingTitle(text.replaceAll("\\s+", ""));
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
        if (isDayaEnumerationContinuation(paragraphs, startIndex, automatic, numberingKey)) {
            return List.of();
        }
        List<XWPFParagraph> group = new ArrayList<>();
        group.add(first);
        List<XWPFParagraph> pendingContinuations = new ArrayList<>();
        int itemCount = 1;
        for (int index = startIndex + 1; index < paragraphs.size(); index++) {
            XWPFParagraph paragraph = paragraphs.get(index);
            String text = normalize(paragraph.getText());
            boolean matchingKind = automatic
                    ? hasAutomaticNumbering(paragraph) && numberingKey.equals(numberingKey(paragraph))
                    : !hasAutomaticNumbering(paragraph) && DayaEnumerationRules.isLeadingListItem(text);
            if (matchingKind && isDayaEnumerationItemCandidate(paragraph, text)) {
                group.addAll(pendingContinuations);
                pendingContinuations.clear();
                group.add(paragraph);
                itemCount++;
                continue;
            }
            if (hasAutomaticNumbering(paragraph) || DayaEnumerationRules.isLeadingListItem(text)) break;
            if (!isDayaEnumerationItemCandidate(paragraph, text)) break;
            pendingContinuations.add(paragraph);
        }
        if (itemCount < 2) return List.of();
        String merged = mergedEnumerationText(group);
        if (merged.length() < MIN_PARAGRAPH_CHARACTERS
                || (countCjk(merged) < 10 && countLatinLetters(merged) < 40)) {
            return List.of();
        }
        return List.copyOf(group);
    }

    private boolean isDayaEnumerationContinuation(List<XWPFParagraph> paragraphs, int startIndex,
                                                  boolean automatic, String numberingKey) {
        if (startIndex <= 0) return false;
        XWPFParagraph previous = paragraphs.get(startIndex - 1);
        String previousText = normalize(previous.getText());
        boolean matchingKind = automatic
                ? hasAutomaticNumbering(previous) && numberingKey.equals(numberingKey(previous))
                : !hasAutomaticNumbering(previous) && DayaEnumerationRules.isLeadingListItem(previousText);
        return matchingKind && isDayaEnumerationItemCandidate(previous, previousText);
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
        int itemIndex = 0;
        for (XWPFParagraph paragraph : paragraphs) {
            String text = normalize(paragraph.getText());
            String prepared = isDayaEnumerationMarker(paragraph, text)
                    ? DayaEnumerationRules.mergedItemText(++itemIndex, text)
                    : text;
            appendEnumerationItem(merged, prepared);
        }
        return merged.toString();
    }

    private boolean isDayaEnumerationMarker(XWPFParagraph paragraph, String text) {
        return hasAutomaticNumbering(paragraph) || DayaEnumerationRules.isLeadingListItem(text);
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

    private boolean hasOnlyDayaTableTextRuns(XWPFParagraph paragraph) {
        Node paragraphNode = paragraph.getCTP().getDomNode();
        boolean textRunSeen = false;
        for (Node child = paragraphNode.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            String name = child.getLocalName();
            if ("pPr".equals(name)) continue;
            if (!"r".equals(name)) return false;
            textRunSeen = true;
            for (Node runChild = child.getFirstChild(); runChild != null; runChild = runChild.getNextSibling()) {
                if (runChild.getNodeType() != Node.ELEMENT_NODE) continue;
                String runName = runChild.getLocalName();
                if (!"rPr".equals(runName) && !"t".equals(runName)) return false;
            }
        }
        return textRunSeen;
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
        return isReferenceTitle(text) || isAcknowledgementsTitle(text)
                || isProtectedTrailingTitle(compact);
    }

    private boolean isReferenceTitle(String text) {
        String compact = text.replaceAll("\\s+", "");
        return compact.matches("^" + TRAILING_NUMBER_PREFIX + "参考文献[:：]?$")
                || compact.matches("(?i)^" + TRAILING_NUMBER_PREFIX + "references[:：]?$");
    }

    private boolean isAcknowledgementsTitle(String text) {
        String compact = text.replaceAll("\\s+", "");
        return compact.matches("^" + TRAILING_NUMBER_PREFIX + "致谢[:：]?$");
    }

    private boolean isProtectedTrailingTitle(String compactText) {
        return compactText.matches("^" + TRAILING_NUMBER_PREFIX
                + "(附录(?:[A-Z一二三四五六七八九十])?|作者简介|"
                + "声明|原创性声明|学位论文原创性声明|评阅意见|学术评价).*$");
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

    private void validateCandidate(Target target, String rewritten) {
        String original = target.originalText();
        boolean dayaTableText = target.preparation() == DayaPreparation.TABLE_TEXT;
        if (rewritten.startsWith("```") || rewritten.contains("以下是改写") || rewritten.contains("改写结果：")) {
            throw new IllegalStateException("改写段落包含模型说明文字");
        }
        if (dayaTableText) {
            if (rewritten.indexOf('\r') >= 0 || rewritten.indexOf('\n') >= 0
                    || rewritten.indexOf('\t') >= 0) {
                throw new IllegalStateException("大雅表格说明不得新增换行或制表符");
            }
            if (!dayaTableInvariants(original).equals(dayaTableInvariants(rewritten))) {
                throw new IllegalStateException("大雅表格说明未完整保留编号、数据、单位或否定条件");
            }
        }
        DayaRewriteQualityRules.validateFinal(original, rewritten, target.context());
    }

    private List<String> dayaTableInvariants(String text) {
        List<String> invariants = new ArrayList<>();
        Matcher matcher = DAYA_TABLE_INVARIANT.matcher(text == null ? "" : text);
        while (matcher.find()) invariants.add(matcher.group());
        return List.copyOf(invariants);
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
        int itemIndex = 0;
        for (XWPFParagraph paragraph : target.sourceParagraphs()) {
            StyledParagraph styled = protectStyledRuns(
                    paragraph, normalize(paragraph.getText()), sequence);
            String prepared = isDayaEnumerationMarker(paragraph, normalize(paragraph.getText()))
                    ? DayaEnumerationRules.mergedItemText(++itemIndex, styled.modelText())
                    : styled.modelText();
            appendEnumerationItem(modelText, prepared);
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
            if (DayaEnumerationRules.isPureOrderingMarkerRun(text)) {
                modelText.append(text);
                continue;
            }
            int markerEnd = DayaEnumerationRules.leadingEditableMarkerEnd(text);
            if (markerEnd > 0 && markerEnd < text.length()) {
                String token = "[[DROP_STYLE_PROTECTED_" + sequence.getAndIncrement() + "]]";
                CTRPr properties = run.getCTR().isSetRPr()
                        ? (CTRPr) run.getCTR().getRPr().copy()
                        : null;
                fragments.put(token, new StyledFragment(text.substring(markerEnd), properties));
                modelText.append(text, 0, markerEnd).append(token);
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
        if (!run.getCTR().isSetRPr()) return "";
        CTRPr properties = (CTRPr) run.getCTR().getRPr().copy();

        while (properties.sizeOfLangArray() > 0) properties.removeLang(0);
        while (properties.sizeOfNoProofArray() > 0) properties.removeNoProof(0);
        for (int index = properties.sizeOfRFontsArray() - 1; index >= 0; index--) {
            CTFonts fonts = properties.getRFontsArray(index);
            if (fonts.isSetHint()) fonts.unsetHint();
            if (!hasVisibleFontSelection(fonts)) properties.removeRFonts(index);
        }

        Node root = properties.getDomNode();
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) return properties.xmlText();
        }
        return "";
    }

    private XWPFRun baseRun(List<XWPFRun> runs) {
        Map<String, Integer> charactersByStyle = new LinkedHashMap<>();
        Map<String, XWPFRun> representativeByStyle = new LinkedHashMap<>();
        for (XWPFRun run : runs) {
            String text = run.text();
            if (text == null || text.isEmpty()) continue;
            String signature = runPropertiesSignature(run);
            charactersByStyle.merge(signature, text.length(), Integer::sum);
            XWPFRun representative = representativeByStyle.get(signature);
            if (representative == null || text.length() > representative.text().length()) {
                representativeByStyle.put(signature, run);
            }
        }
        if (charactersByStyle.isEmpty()) return runs.get(0);

        String mainStyle = null;
        int mainCharacters = -1;
        for (Map.Entry<String, Integer> entry : charactersByStyle.entrySet()) {
            if (entry.getValue() > mainCharacters) {
                mainStyle = entry.getKey();
                mainCharacters = entry.getValue();
            }
        }
        return representativeByStyle.get(mainStyle);
    }

    private boolean hasVisibleFontSelection(CTFonts fonts) {
        return fonts.isSetAscii() || fonts.isSetHAnsi()
                || fonts.isSetEastAsia() || fonts.isSetCs()
                || fonts.isSetAsciiTheme() || fonts.isSetHAnsiTheme()
                || fonts.isSetEastAsiaTheme() || fonts.isSetCstheme();
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

    private void writeAtomically(XWPFDocument document, Path output,
                                 DayaTableStructureSnapshot tableSnapshot) throws Exception {
        Files.createDirectories(output.toAbsolutePath().getParent());
        Path temporary = output.resolveSibling(output.getFileName() + "." + UUID.randomUUID() + ".part");
        try {
            try (OutputStream stream = Files.newOutputStream(temporary)) {
                document.write(stream);
            }
            if (Files.size(temporary) <= 0) throw new IllegalStateException("生成的 DOCX 文件为空");
            if (!tableSnapshot.tables().isEmpty()) {
                try (InputStream stream = Files.newInputStream(temporary);
                     XWPFDocument verified = new XWPFDocument(stream)) {
                    tableSnapshot.validate(verified);
                }
            }
            try {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String xml(org.apache.xmlbeans.XmlObject value) {
        return value == null ? "" : value.xmlText();
    }

    private record DayaTableStructureSnapshot(List<DayaTableShape> tables) {
        private static DayaTableStructureSnapshot capture(XWPFDocument document) {
            return new DayaTableStructureSnapshot(document.getTables().stream()
                    .map(DayaTableShape::capture).toList());
        }

        private void validate(XWPFDocument document) {
            if (!equals(capture(document))) {
                throw new IllegalStateException("大雅表格结构保护校验失败，未写入结果文件");
            }
        }
    }

    private record DayaTableShape(String properties, String grid, List<DayaTableRowShape> rows) {
        private static DayaTableShape capture(XWPFTable table) {
            return new DayaTableShape(xml(table.getCTTbl().getTblPr()),
                    xml(table.getCTTbl().getTblGrid()),
                    table.getRows().stream().map(DayaTableRowShape::capture).toList());
        }
    }

    private record DayaTableRowShape(String properties, List<DayaTableCellShape> cells) {
        private static DayaTableRowShape capture(XWPFTableRow row) {
            return new DayaTableRowShape(xml(row.getCtRow().getTrPr()),
                    row.getTableCells().stream().map(DayaTableCellShape::capture).toList());
        }
    }

    private record DayaTableCellShape(String properties, int paragraphCount, int nestedTableCount,
                                      List<String> paragraphProperties) {
        private static DayaTableCellShape capture(XWPFTableCell cell) {
            return new DayaTableCellShape(xml(cell.getCTTc().getTcPr()),
                    cell.getParagraphs().size(), cell.getTables().size(),
                    cell.getParagraphs().stream()
                            .map(paragraph -> paragraph.getCTP().isSetPPr()
                                    ? xml(paragraph.getCTP().getPPr()) : "")
                            .toList());
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

    private record PreparedBatch(
            int index,
            Batch batch,
            Map<String, PlatformDocumentTextProtector.ProtectedText> protectedById,
            Map<String, StyledParagraph> styledById,
            List<PlatformDoubaoRewriteGateway.Segment> segments) { }

    private record ParagraphRewrite(Target target, StyledRestore restored) { }

    private record RewriteAttempt(ParagraphRewrite rewrite, String failure) { }

    private record RetryTarget(
            int batchIndex,
            PreparedBatch prepared,
            Target target,
            PlatformDoubaoRewriteGateway.Segment segment,
            String firstFailure) { }

    private record RetryResult(int batchIndex, ParagraphRewrite rewrite, String failure) { }

    private record BatchResult(
            int index,
            int targetCount,
            List<ParagraphRewrite> rewrites,
            int failed,
            List<String> failureMessages,
            List<RetryTarget> retryTargets) { }

    private enum DayaSection {
        INCLUDE,
        SKIP
    }

    enum DayaPreparation {
        NONE,
        INLINE_NUMERIC,
        MERGED_LIST,
        TABLE_TEXT
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
