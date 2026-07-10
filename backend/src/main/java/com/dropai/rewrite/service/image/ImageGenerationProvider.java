package com.dropai.rewrite.service.image;

public interface ImageGenerationProvider {
    ImageGenerationResult generate(ImageGenerationRequest request);
    ImageGenerationResult health();
}
