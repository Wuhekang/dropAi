package com.dropai.rewrite.mechanicalassistant;

public interface MechanicalAiGateway {
    String generate(String instructions, String input);
    boolean available();
}
