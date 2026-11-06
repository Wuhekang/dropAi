package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.TableKind;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class TableMetricsCalculator {
    private final DeterministicTextMetricsService textMetrics;

    public TableMetricsCalculator(DeterministicTextMetricsService textMetrics) {
        this.textMetrics = Objects.requireNonNull(textMetrics, "textMetrics");
    }

    public TableMetricsResult calculate(TableMetricsRequest request) {
        Objects.requireNonNull(request, "request");
        int fontSize = request.defaultFontSizeHundredthPt();
        while (true) {
            TableMetricsResult result = tryAtSize(request, fontSize);
            if (result != null) {
                return result;
            }
            if (fontSize == request.minimumFontSizeHundredthPt()) {
                break;
            }
            fontSize = Math.max(
                    request.minimumFontSizeHundredthPt(),
                    fontSize - DeterministicTextMetricsService.FONT_STEP_HUNDREDTH_PT);
        }
        throw new MeasurementException(
                PptQualityCode.TABLE_CAPACITY_EXCEEDED,
                "Table cannot fit without dropping rows, truncating cells, or using a font below the minimum");
    }

    private TableMetricsResult tryAtSize(TableMetricsRequest request, int fontSize) {
        List<Long> minimumWidths = minimumColumnWidths(request, fontSize);
        long minimumTotal;
        try {
            minimumTotal = minimumWidths.stream().reduce(0L, Math::addExact);
        } catch (ArithmeticException exception) {
            return null;
        }
        if (minimumTotal > request.tableWidthEmu()) {
            return null;
        }
        List<Long> weights = columnWeights(request, fontSize);
        List<Long> widths = distributeWidth(
                request.tableWidthEmu(),
                minimumWidths,
                weights);

        List<String> renderedHeaders = new ArrayList<>();
        List<List<String>> renderedRows = new ArrayList<>();
        long headerContentHeight = 0L;
        long bodyContentHeight = 0L;

        for (int column = 0; column < request.headers().size(); column++) {
            long contentWidth = widths.get(column) - 2L * request.horizontalCellPaddingEmu();
            TextFitResult fitted = fitCell(
                    request,
                    request.headers().get(column),
                    request.headerWeight(),
                    fontSize,
                    contentWidth,
                    request.headerMaxLines());
            if (!fitted.fits()) {
                return null;
            }
            renderedHeaders.add(fitted.renderedText());
            headerContentHeight = Math.max(headerContentHeight, fitted.requiredHeightEmu());
        }

        for (List<String> row : request.rows()) {
            List<String> renderedRow = new ArrayList<>();
            for (int column = 0; column < row.size(); column++) {
                if (row.get(column).isEmpty()) {
                    renderedRow.add("");
                    bodyContentHeight = Math.max(
                            bodyContentHeight,
                            textMetrics.naturalLineHeightEmu(
                                    request.fontProfile(),
                                    request.fontRole(),
                                    request.bodyWeight(),
                                    fontSize));
                    continue;
                }
                long contentWidth = widths.get(column) - 2L * request.horizontalCellPaddingEmu();
                TextFitResult fitted = fitCell(
                        request,
                        row.get(column),
                        request.bodyWeight(),
                        fontSize,
                        contentWidth,
                        request.bodyMaxLines());
                if (!fitted.fits()) {
                    return null;
                }
                renderedRow.add(fitted.renderedText());
                bodyContentHeight = Math.max(bodyContentHeight, fitted.requiredHeightEmu());
            }
            renderedRows.add(List.copyOf(renderedRow));
        }

        long doubleVerticalPadding = Math.multiplyExact(2L, request.verticalCellPaddingEmu());
        long headerHeight = Math.addExact(headerContentHeight, doubleVerticalPadding);
        long bodyHeight = Math.addExact(bodyContentHeight, doubleVerticalPadding);
        long totalHeight = Math.addExact(
                headerHeight,
                Math.multiplyExact(bodyHeight, request.rows().size()));
        if (totalHeight > request.maximumTableHeightEmu()) {
            return null;
        }

        ResolvedFontFace bodyFace = request.fontProfile().requireFace(request.fontRole(), request.bodyWeight());
        return new TableMetricsResult(
                fontSize == request.defaultFontSizeHundredthPt()
                        ? TableFitStatus.FIT
                        : TableFitStatus.FIT_WITH_FONT_SCALE,
                fontSize,
                bodyFace.selectedFamily(),
                bodyFace.fontFingerprint(),
                widths,
                headerHeight,
                bodyHeight,
                totalHeight,
                renderedHeaders,
                renderedRows);
    }

    private TextFitResult fitCell(
            TableMetricsRequest request,
            String value,
            int weight,
            int fontSize,
            long contentWidth,
            int maxLines
    ) {
        if (contentWidth <= 0) {
            return unfitCell(request, value, weight, fontSize);
        }
        return textMetrics.fit(new TextFitRequest(
                value,
                request.fontProfile(),
                request.fontRole(),
                weight,
                fontSize,
                fontSize,
                request.lineSpacingPermille(),
                contentWidth,
                request.maximumTableHeightEmu(),
                maxLines));
    }

    private TextFitResult unfitCell(
            TableMetricsRequest request,
            String value,
            int weight,
            int fontSize
    ) {
        ResolvedFontFace face = request.fontProfile().requireFace(request.fontRole(), weight);
        return new TextFitResult(
                TextFitStatus.UNFIT,
                value,
                "",
                List.of(),
                face.selectedFamily(),
                face.fontFingerprint(),
                fontSize,
                request.lineSpacingPermille(),
                0L,
                0,
                0,
                "No positive cell content width remains after padding");
    }

    private List<Long> minimumColumnWidths(TableMetricsRequest request, int fontSize) {
        List<Long> result = new ArrayList<>();
        for (int column = 0; column < request.headers().size(); column++) {
            long longest = longestUnbreakableWidth(
                    request,
                    request.headers().get(column),
                    request.headerWeight(),
                    fontSize);
            for (List<String> row : request.rows()) {
                longest = Math.max(
                        longest,
                        longestUnbreakableWidth(
                                request,
                                row.get(column),
                                request.bodyWeight(),
                                fontSize));
            }
            long padded = Math.addExact(
                    longest,
                    Math.multiplyExact(2L, request.horizontalCellPaddingEmu()));
            result.add(Math.max(request.minimumColumnWidthEmu(), padded));
        }
        return List.copyOf(result);
    }

    private long longestUnbreakableWidth(
            TableMetricsRequest request,
            String value,
            int weight,
            int fontSize
    ) {
        long longest = 0L;
        for (String token : unbreakableTokens(value)) {
            longest = Math.max(
                    longest,
                    textMetrics.widthEmu(
                            request.fontProfile(),
                            request.fontRole(),
                            weight,
                            fontSize,
                            token));
        }
        return longest;
    }

    private List<Long> columnWeights(TableMetricsRequest request, int fontSize) {
        int count = request.headers().size();
        if (request.tableKind() == TableKind.ENTITY_PURPOSE && count == 2) {
            return List.of(35L, 65L);
        }
        if (request.tableKind() == TableKind.TEST_RESULT && count == 4) {
            return List.of(16L, 18L, 52L, 14L);
        }
        List<Long> weights = new ArrayList<>();
        for (int column = 0; column < count; column++) {
            long preferred = textMetrics.widthEmu(
                    request.fontProfile(),
                    request.fontRole(),
                    request.headerWeight(),
                    fontSize,
                    request.headers().get(column));
            for (List<String> row : request.rows()) {
                preferred = Math.max(
                        preferred,
                        textMetrics.widthEmu(
                                request.fontProfile(),
                                request.fontRole(),
                                request.bodyWeight(),
                                fontSize,
                                row.get(column)));
            }
            weights.add(Math.max(1L, preferred));
        }
        return List.copyOf(weights);
    }

    private List<Long> distributeWidth(long total, List<Long> minimums, List<Long> weights) {
        List<Long> result = new ArrayList<>(java.util.Collections.nCopies(minimums.size(), 0L));
        boolean[] fixed = new boolean[minimums.size()];
        long remaining = total;
        while (true) {
            List<Integer> active = new ArrayList<>();
            for (int column = 0; column < fixed.length; column++) {
                if (!fixed[column]) {
                    active.add(column);
                }
            }
            List<Long> shares = proportionalShares(remaining, weights, active);
            List<Integer> violations = new ArrayList<>();
            for (int index = 0; index < active.size(); index++) {
                int column = active.get(index);
                if (shares.get(index) < minimums.get(column)) {
                    violations.add(column);
                }
            }
            if (violations.isEmpty()) {
                for (int index = 0; index < active.size(); index++) {
                    result.set(active.get(index), shares.get(index));
                }
                return List.copyOf(result);
            }
            for (int column : violations) {
                fixed[column] = true;
                result.set(column, minimums.get(column));
                remaining = Math.subtractExact(remaining, minimums.get(column));
            }
        }
    }

    private List<Long> proportionalShares(long total, List<Long> allWeights, List<Integer> columns) {
        BigInteger weightTotal = columns.stream()
                .map(column -> BigInteger.valueOf(allWeights.get(column)))
                .reduce(BigInteger.ZERO, BigInteger::add);
        List<Long> shares = new ArrayList<>();
        List<Remainder> remainders = new ArrayList<>();
        long distributed = 0L;
        for (int index = 0; index < columns.size(); index++) {
            int column = columns.get(index);
            BigInteger product = BigInteger.valueOf(total)
                    .multiply(BigInteger.valueOf(allWeights.get(column)));
            BigInteger[] share = product.divideAndRemainder(weightTotal);
            long whole = share[0].longValueExact();
            shares.add(whole);
            distributed = Math.addExact(distributed, whole);
            remainders.add(new Remainder(index, share[1]));
        }
        long unitsLeft = total - distributed;
        remainders.sort(Comparator
                .comparing(Remainder::remainder, Comparator.reverseOrder())
                .thenComparingInt(Remainder::column));
        for (int index = 0; index < unitsLeft; index++) {
            int shareIndex = remainders.get(index).column();
            shares.set(shareIndex, shares.get(shareIndex) + 1L);
        }
        return List.copyOf(shares);
    }

    private List<String> unbreakableTokens(String value) {
        if (value.isEmpty()) {
            return List.of("");
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder currentWord = new StringBuilder();
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isWordCodePoint(codePoint)) {
                currentWord.appendCodePoint(codePoint);
            } else {
                if (!currentWord.isEmpty()) {
                    tokens.add(currentWord.toString());
                    currentWord.setLength(0);
                }
                if (!Character.isWhitespace(codePoint)) {
                    tokens.add(new String(Character.toChars(codePoint)));
                }
            }
        }
        if (!currentWord.isEmpty()) {
            tokens.add(currentWord.toString());
        }
        return tokens.isEmpty() ? List.of("") : List.copyOf(tokens);
    }

    private boolean isWordCodePoint(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        boolean cjk = script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
        return !cjk && (Character.isLetterOrDigit(codePoint)
                || codePoint == '.'
                || codePoint == '_'
                || codePoint == '-'
                || codePoint == '/'
                || codePoint == '+'
                || codePoint == '#'
                || codePoint == '@'
                || codePoint == ':');
    }

    private record Remainder(int column, BigInteger remainder) {
    }
}
