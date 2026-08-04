package com.dropai.rewrite;

import com.dropai.rewrite.mechanicalengine.cadcore.FeatureInterpreter;
import com.dropai.rewrite.mechanicalengine.cadcore.MechanicalDesignCadConverter;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalDesignSpec;
import com.dropai.rewrite.mechanicalengine.service.MechanicalChiefEngineer;
import com.dropai.rewrite.mechanicalengine.service.MechanicalDesignPlanner;
import com.dropai.rewrite.mechanicalengine.service.MechanicalDesignQualityValidator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MechanicalDesignSkillTests {
    private final MechanicalDesignQualityValidator quality = new MechanicalDesignQualityValidator();
    private final MechanicalDesignCadConverter converter = new MechanicalDesignCadConverter();
    private final MechanicalChiefEngineer chief = new MechanicalChiefEngineer(new MechanicalDesignPlanner(), quality, converter);

    @Test
    void automaticClampProducesCompleteMechanicalDesignSpec() {
        MechanicalDesignSpec spec = chief.designSpec("设计自动夹具");
        assertEquals("automatic_clamp", spec.product().type());
        assertDoesNotThrow(() -> quality.validate(spec));
        Set<String> modules = spec.modules().stream().map(MechanicalDesignSpec.Module::name).collect(java.util.stream.Collectors.toSet());
        assertTrue(modules.containsAll(Set.of("夹持模块", "驱动模块", "底座模块", "导向模块")));
        assertTrue(spec.parts().stream().allMatch(part -> !part.function().isBlank()
                && !part.material().isBlank() && !part.manufacturing().isBlank()
                && !part.cadRequirements().isEmpty()));
        assertTrue(spec.parameters().stream().allMatch(parameter -> !parameter.engineeringReason().isBlank()));
        assertFalse(spec.assemblyIntent().isEmpty());
    }

    @Test
    void mechanicalDesignSpecConvertsToExecutableFeatureSpec() {
        MechanicalDesignSpec design = chief.designSpec("automatic clamp");
        var cad = converter.convert("test-clamp", design);
        assertEquals(design.parts().size(), cad.parts().size());
        assertEquals(design.assemblyIntent().size(), cad.assembly().constraints().size());
        assertDoesNotThrow(() -> new FeatureInterpreter().validate(cad));
        assertTrue(cad.parts().stream().allMatch(part -> part.body().stream().anyMatch(feature -> "SKETCH".equals(feature.featureType()))));
        assertTrue(cad.parts().stream().allMatch(part -> part.body().stream().anyMatch(feature -> "PAD".equals(feature.featureType()))));
        assertTrue(cad.parts().stream().flatMap(part -> part.body().stream()).noneMatch(feature ->
                Set.of("BOX", "CUBE", "CYLINDER", "SPHERE", "PRIMITIVE").contains(feature.featureType())));
    }

    @Test
    void unsupportedProductDoesNotReceiveAConceptualFakeDesign() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> chief.designSpec("设计一台未知设备"));
        assertTrue(error.getMessage().contains("UNSUPPORTED_MECHANICAL_PRODUCT"));
    }
}
