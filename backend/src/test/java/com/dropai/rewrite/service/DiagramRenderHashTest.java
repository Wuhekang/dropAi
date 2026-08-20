package com.dropai.rewrite.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiagramRenderHashTest {
    @Test void normalizationOnlyChangesBomLineEndingsTrailingSpacesAndFinalBlankLines(){
        String a="\uFEFF@Flowchart\r\n标题：A  \r\n[节点]\r\nN1|start|开始\r\n\r\n";
        String b="@Flowchart\n标题：A\n[节点]\nN1|start|开始";
        assertEquals(DiagramService.normalizeDsl(b),DiagramService.normalizeDsl(a));
        assertEquals(DiagramService.renderHash(1,2,"flowchart",DiagramService.normalizeDsl(a)),DiagramService.renderHash(1,2,"flowchart",DiagramService.normalizeDsl(b)));
    }
    @Test void projectAndSemanticContentAffectHash(){
        String dsl=DiagramService.normalizeDsl("@Flowchart\n标题：A");
        assertNotEquals(DiagramService.renderHash(1,2,"flowchart",dsl),DiagramService.renderHash(1,3,"flowchart",dsl));
        assertNotEquals(DiagramService.renderHash(1,2,"flowchart",dsl),DiagramService.renderHash(1,2,"flowchart",DiagramService.normalizeDsl("@Flowchart\n标题：B")));
    }
}
