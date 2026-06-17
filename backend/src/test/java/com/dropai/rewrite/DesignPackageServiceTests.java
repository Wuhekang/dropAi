package com.dropai.rewrite;

import com.dropai.rewrite.auth.AuthContext;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.entity.DocumentJobRecord;
import com.dropai.rewrite.modules.calculationEngine.CalculationEngine;
import com.dropai.rewrite.modules.designEnhancementEngine.DesignEnhancementEngine;
import com.dropai.rewrite.modules.drawingEngine.DrawingEngine;
import com.dropai.rewrite.modules.exportEngine.ExportEngine;
import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.paperEngine.PaperEngine;
import com.dropai.rewrite.modules.parameterEngine.ParameterEngine;
import com.dropai.rewrite.modules.swMacroEngine.SwMacroEngine;
import com.dropai.rewrite.modules.structureEngine.StructureEngine;
import com.dropai.rewrite.service.DesignPackageService;
import com.dropai.rewrite.vo.DesignPackageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class DesignPackageServiceTests {
    @AfterEach
    void clearAuth() { AuthContext.clear(); }

    @Test
    void successfulArtifactsHaveRealDownloadMetadata() {
        DocumentJobMapper mapper = mock(DocumentJobMapper.class);
        when(mapper.insert(any(DocumentJobRecord.class))).thenReturn(1);
        DesignPackageService service = new DesignPackageService(
                new ParameterEngine(), new CalculationEngine(), new DesignEnhancementEngine(), new StructureEngine(), new DrawingEngine(), new SwMacroEngine(),
                new PaperEngine(), new ExportEngine(new ObjectMapper()), mapper);
        AuthContext.setUserId(1L);

        DesignPackageVO result = service.generate(new DesignProject());

        assertEquals("success", result.getStatus());
        assertTrue(result.getArtifacts().stream().allMatch(item -> "success".equals(item.getStatus())));
        assertTrue(result.getArtifacts().stream().allMatch(item -> item.getSize() > 0 && item.getDownloadUrl() != null));
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "paper.docx".equals(item.getName())));
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "assembly.dxf".equals(item.getName())));
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "cad_preview.png".equals(item.getName())));
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "preview.png".equals(item.getName())));
        assertTrue(result.getProject().getBom().size() >= 5);
        assertTrue(result.getArtifacts().stream().anyMatch(item -> "project_package.zip".equals(item.getName())));
        ArgumentCaptor<DocumentJobRecord> captor = ArgumentCaptor.forClass(DocumentJobRecord.class);
        verify(mapper, org.mockito.Mockito.atLeastOnce()).insert(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(record -> record.getMode() != null && record.getMode().length() <= 10));
        assertTrue(captor.getAllValues().stream().filter(record -> record.getFileName().endsWith(".docx")).allMatch(record -> "docx".equals(record.getMode())));
    }
}
