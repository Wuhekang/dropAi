package com.dropai.rewrite.mechanicalengine.knowledge;

import java.util.List;

public record ProductFamilyKnowledge(String family, List<String> typicalFunctions, List<String> candidateModules,
                                     List<String> designRules, List<String> parameterRules,
                                     List<String> partPatterns) {}
