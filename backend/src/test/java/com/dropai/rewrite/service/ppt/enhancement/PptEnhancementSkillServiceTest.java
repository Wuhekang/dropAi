package com.dropai.rewrite.service.ppt.enhancement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PptEnhancementSkillServiceTest {
    @Test
    void packagesTheCompleteDetailedSkillForDoubaoWithStableHashes() {
        PptEnhancementSkillService service = new PptEnhancementSkillService();
        var first = service.requireBundle();
        var second = service.requireBundle();

        assertEquals("ppt-enhancement", first.name());
        assertEquals("1.2.0", first.version());
        assertEquals(64, first.hash().length());
        assertEquals(first.hash(), second.hash());
        assertEquals(3, first.resources().size());
        assertTrue(first.trustedPrompt().length() > 15_000);
        assertTrue(first.trustedPrompt().contains("Visual recipes"));
        assertTrue(first.trustedPrompt().contains("Quality assurance and enhancement logging"));
        assertTrue(first.trustedPrompt().contains("DOUBAO_ENHANCEMENT_RULES_BEGIN"));
        assertTrue(first.trustedPrompt().contains("at most eight independent full-slide previews"));
    }
}
