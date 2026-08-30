package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.TableKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableMetricsCalculatorTest {
    private final ResolvedFontProfile profile = MeasurementTestSupport.exactProfile();
    private final TableMetricsCalculator calculator =
            new TableMetricsCalculator(MeasurementTestSupport.textMetrics());

    @Test
    void entityPurposeTableProducesExactColumnSumAndEditableWrappedCells() {
        TableMetricsRequest request = request(
                TableKind.ENTITY_PURPOSE,
                List.of("业务表", "业务用途"),
                List.of(
                        List.of("user", "用户账户管理"),
                        List.of("health_record", "健康数据记录"),
                        List.of("assessment", "AI评估结果"),
                        List.of("health_goal", "健康目标管理")),
                5_000_000L,
                4_500_000L);

        TableMetricsResult result = calculator.calculate(request);

        assertEquals(5_000_000L, result.columnWidthsEmu().stream().mapToLong(Long::longValue).sum());
        assertEquals(2, result.columnWidthsEmu().size());
        assertEquals(4, result.renderedRows().size());
        assertTrue(result.totalHeightEmu() <= 4_500_000L);
        assertTrue(result.columnWidthsEmu().get(1) > result.columnWidthsEmu().get(0));
    }

    @Test
    void testResultSemanticWeightsGiveTheFindingColumnTheLargestShare() {
        TableMetricsResult result = calculator.calculate(request(
                TableKind.TEST_RESULT,
                List.of("测试模块", "用例范围", "验证结果", "状态"),
                List.of(
                        List.of("用户登录", "T-01至T-04", "空提交与错误密码均被拦截", "通过"),
                        List.of("数据可视化", "T-16至T-19", "图表正常渲染且数值映射准确", "通过")),
                6_800_000L,
                4_800_000L));

        assertEquals(6_800_000L, result.columnWidthsEmu().stream().mapToLong(Long::longValue).sum());
        assertTrue(result.columnWidthsEmu().get(2) > result.columnWidthsEmu().get(0));
        assertTrue(result.columnWidthsEmu().get(2) > result.columnWidthsEmu().get(1));
        assertTrue(result.columnWidthsEmu().get(2) > result.columnWidthsEmu().get(3));
    }

    @Test
    void repeatedCalculationProducesIdenticalWidthsLinesAndHeights() {
        TableMetricsRequest request = request(
                TableKind.ENTITY_PURPOSE,
                List.of("业务表", "业务用途"),
                List.of(
                        List.of("user", "用户账户管理"),
                        List.of("assessment", "AI评估结果")),
                3_000_003L,
                4_500_000L);
        TableMetricsResult first = calculator.calculate(request);

        for (int attempt = 0; attempt < 100; attempt++) {
            assertEquals(first, calculator.calculate(request));
        }
        assertEquals(3_000_003L, first.columnWidthsEmu().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    void tableFontReductionUsesTheSameFixedHalfPointStep() {
        TableMetricsResult result = calculator.calculate(request(
                TableKind.GENERIC,
                List.of("ABCDEFGHIJ", "KLMNOPQRST"),
                List.of(List.of("A", "B")),
                2_800_000L,
                3_000_000L));

        assertEquals(TableFitStatus.FIT_WITH_FONT_SCALE, result.status());
        assertEquals(1_750, result.fontSizeHundredthPt());
    }

    @Test
    void tableCapacityFailureDoesNotDeleteRowsOrDropBelowMinimumFontSize() {
        TableMetricsRequest request = request(
                TableKind.TEST_RESULT,
                List.of("测试模块", "用例范围", "验证结果", "状态"),
                List.of(
                        List.of("用户登录", "T-01至T-04", "空提交与错误密码均被拦截", "通过"),
                        List.of("健康数据管理", "T-05至T-09", "数据录入和异常校验均符合预期", "通过"),
                        List.of("AI健康评估", "T-10至T-12", "评估报告和历史查询符合预期", "通过")),
                3_000_000L,
                100_000L);

        MeasurementException exception = assertThrows(
                MeasurementException.class,
                () -> calculator.calculate(request));

        assertEquals(PptQualityCode.TABLE_CAPACITY_EXCEEDED, exception.qualityCode());
    }

    @Test
    void emptyCellsRemainEditableEmptyStringsAndStillReserveOneLineHeight() {
        TableMetricsResult result = calculator.calculate(request(
                TableKind.GENERIC,
                List.of("字段", "说明"),
                List.of(List.of("optional_note", "")),
                3_200_000L,
                2_000_000L));

        assertEquals("", result.renderedRows().get(0).get(1));
        assertTrue(result.bodyRowHeightEmu() > 100_000L);
    }

    private TableMetricsRequest request(
            TableKind kind,
            List<String> headers,
            List<List<String>> rows,
            long width,
            long height
    ) {
        return new TableMetricsRequest(
                kind,
                headers,
                rows,
                profile,
                "body",
                600,
                400,
                1_800,
                1_600,
                1_300,
                width,
                height,
                80_000L,
                50_000L,
                300_000L,
                2,
                3);
    }
}
