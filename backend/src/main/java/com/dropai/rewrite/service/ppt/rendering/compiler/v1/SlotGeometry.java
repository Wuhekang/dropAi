package com.dropai.rewrite.service.ppt.rendering.compiler.v1;

import com.dropai.rewrite.service.ppt.rendering.layout.v1.LayoutRecipe;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

record SlotGeometry(long xEmu, long yEmu, long widthEmu, long heightEmu) {
    private static final MathContext MC = new MathContext(24, RoundingMode.HALF_UP);

    static SlotGeometry from(JsonNode slot, ThemePlanStyleResolver theme) {
        if (slot == null || !slot.isObject()) {
            throw new IllegalArgumentException("Layout slot must be an object");
        }
        int column = slot.path("gridColumn").asInt();
        int span = slot.path("gridSpan").asInt();
        int columns = theme.gridColumns();
        if (column < 1 || span < 1 || column + span - 1 > columns) {
            throw new IllegalArgumentException("Layout slot exceeds the resolved theme grid");
        }

        BigDecimal slideWidth = BigDecimal.valueOf(theme.slideWidthEmu())
                .divide(Emu.PER_INCH, MC);
        BigDecimal availableWidth = slideWidth
                .subtract(theme.safeLeftIn(), MC)
                .subtract(theme.safeRightIn(), MC);
        BigDecimal gutter = theme.gutterIn();
        BigDecimal allGutters = gutter.multiply(BigDecimal.valueOf(columns - 1L), MC);
        BigDecimal columnWidth = availableWidth.subtract(allGutters, MC)
                .divide(BigDecimal.valueOf(columns), MC);
        BigDecimal step = columnWidth.add(gutter, MC);
        BigDecimal x = theme.safeLeftIn().add(step.multiply(BigDecimal.valueOf(column - 1L), MC), MC);
        BigDecimal width = columnWidth.multiply(BigDecimal.valueOf(span), MC)
                .add(gutter.multiply(BigDecimal.valueOf(span - 1L), MC), MC);
        return new SlotGeometry(
                Emu.inches(x),
                Emu.inches(slot.path("topIn").decimalValue()),
                Emu.inches(width),
                Emu.inches(slot.path("heightIn").decimalValue()));
    }

    static SlotGeometry from(LayoutRecipe.Slot slot, ThemePlanStyleResolver theme) {
        if (slot == null) {
            throw new IllegalArgumentException("Layout slot must not be null");
        }
        int column = slot.gridColumn();
        int span = slot.gridSpan();
        int columns = theme.gridColumns();
        if (column < 1 || span < 1 || column + span - 1 > columns) {
            throw new IllegalArgumentException("Layout slot exceeds the resolved theme grid");
        }
        BigDecimal slideWidth = BigDecimal.valueOf(theme.slideWidthEmu())
                .divide(Emu.PER_INCH, MC);
        BigDecimal availableWidth = slideWidth
                .subtract(theme.safeLeftIn(), MC)
                .subtract(theme.safeRightIn(), MC);
        BigDecimal gutter = theme.gutterIn();
        BigDecimal columnWidth = availableWidth
                .subtract(gutter.multiply(BigDecimal.valueOf(columns - 1L), MC), MC)
                .divide(BigDecimal.valueOf(columns), MC);
        BigDecimal step = columnWidth.add(gutter, MC);
        BigDecimal x = theme.safeLeftIn().add(step.multiply(BigDecimal.valueOf(column - 1L), MC), MC);
        BigDecimal width = columnWidth.multiply(BigDecimal.valueOf(span), MC)
                .add(gutter.multiply(BigDecimal.valueOf(span - 1L), MC), MC);
        return new SlotGeometry(
                Emu.inches(x),
                Emu.inches(slot.topIn()),
                Emu.inches(width),
                Emu.inches(slot.heightIn()));
    }

    long rightEmu() {
        return xEmu + widthEmu;
    }

    long bottomEmu() {
        return yEmu + heightEmu;
    }
}
