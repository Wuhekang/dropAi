package com.dropai.rewrite;

import com.dropai.rewrite.service.ParametricDxfService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ParametricDxfServiceTests {
    @Test
    void generatesVisibleStandardDxfEntities() {
        String dxf = new String(new ParametricDxfService().generate(1600, 900, 850, 1100, 260), StandardCharsets.US_ASCII);

        assertTrue(dxf.startsWith("0\nSECTION\n2\nHEADER\n"));
        assertTrue(dxf.contains("9\n$EXTMAX\n"));
        assertTrue(dxf.contains("2\nDESIGN_OUTLINE\n"));
        assertTrue(dxf.contains("0\nLINE\n"));
        assertTrue(dxf.contains("0\nCIRCLE\n"));
        assertTrue(dxf.endsWith("0\nEOF\n"));
        assertTrue(dxf.length() > 1500);
    }
}
