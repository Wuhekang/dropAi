package com.dropai.rewrite.service.impl;

import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GezidaParagraphWriterTest {
    @Test
    void retainsSplitCitationSuperscriptAndParagraphStyle() throws Exception {
        try (var doc = new XWPFDocument()) {
            var p = doc.createParagraph();
            p.setStyle("Body");
            p.setIndentationFirstLine(420);
            p.createRun().setText("已有研究讨论岗位差异");
            for (String piece : new String[]{"[", "12", "]"}) {
                var r = p.createRun();
                r.setText(piece);
                r.setSubscript(VerticalAlign.SUPERSCRIPT);
            }
            p.createRun().setText("，还需进一步讨论。");
            GezidaParagraphWriter.replace(p, "岗位差异已有研究涉及[12]，但解释尚需完善。");
            assertThat(p.getText()).isEqualTo("岗位差异已有研究涉及[12]，但解释尚需完善。");
            assertThat(p.getStyle()).isEqualTo("Body");
            assertThat(p.getIndentationFirstLine()).isEqualTo(420);
            assertThat(p.getRuns().subList(1, 4)).allSatisfy(r -> assertThat(r.getCTR().getRPr().getVertAlignArray(0).getVal().toString()).isEqualTo("superscript"));
        }
    }

    @Test
    void rejectsMismatchedCitationsBeforeChangingOriginal() throws Exception {
        try (var doc = new XWPFDocument()) {
            var p = doc.createParagraph();
            p.createRun().setText("原始依据[1]。");
            String before = p.getCTP().xmlText();
            assertThatThrownBy(() -> GezidaParagraphWriter.replace(p, "改写依据[2]。"))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(p.getCTP().xmlText()).isEqualTo(before);
        }
    }
}
