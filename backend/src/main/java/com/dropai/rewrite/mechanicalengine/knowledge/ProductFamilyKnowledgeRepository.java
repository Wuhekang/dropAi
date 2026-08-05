package com.dropai.rewrite.mechanicalengine.knowledge;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalRequirementAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Repository
public class ProductFamilyKnowledgeRepository {
    private static final List<String> FAMILIES = List.of("robot", "fixture", "agv", "conveyor", "mechanism", "machine", "custom");
    private final ObjectMapper mapper;

    public ProductFamilyKnowledgeRepository(ObjectMapper mapper) { this.mapper = mapper; }

    public ProductFamilyKnowledge findFor(MechanicalRequirementAnalysis analysis) {
        String category = analysis.productCategory() == null ? "custom" : analysis.productCategory().toLowerCase(Locale.ROOT);
        String family = FAMILIES.stream().filter(category::contains).findFirst().orElse("custom");
        try (var input = new ClassPathResource("knowledge/mechanical/product-family/" + family + ".json").getInputStream()) {
            return mapper.readValue(input, ProductFamilyKnowledge.class);
        } catch (IOException exception) {
            throw new IllegalStateException("PRODUCT_KNOWLEDGE_UNAVAILABLE: " + family, exception);
        }
    }
}
