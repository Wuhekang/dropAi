package com.dropai.rewrite.mechanicalassistant;

import com.dropai.rewrite.service.MatrixDesignService;
import org.springframework.stereotype.Service;

@Service
public class DoubaoMechanicalAiGateway implements MechanicalAiGateway {
    private final MatrixDesignService model;
    public DoubaoMechanicalAiGateway(MatrixDesignService model) { this.model = model; }
    @Override public String generate(String instructions, String input) { return model.generate(instructions, input); }
    @Override public boolean available() { return model.apiKeyConfigured(); }
}
