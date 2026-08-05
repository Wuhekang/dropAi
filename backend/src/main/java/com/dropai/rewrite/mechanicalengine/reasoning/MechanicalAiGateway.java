package com.dropai.rewrite.mechanicalengine.reasoning;

public interface MechanicalAiGateway {
    String generate(String instructions, String input);
    boolean available();
}
