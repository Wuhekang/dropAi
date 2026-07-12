package com.dropai.rewrite.service.writing;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ManualReferenceProvider implements ReferenceSearchProvider {
    @Override
    public String name() {
        return "manual";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public List<ReferenceCandidate> search(ReferenceSearchQuery query) {
        return List.of();
    }
}
