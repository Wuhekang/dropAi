package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.config.DoubaoProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DoubaoWebSearchProvider implements ReferenceSearchProvider {
    private final DoubaoProperties properties;
    private final boolean webSearchEnabled;

    public DoubaoWebSearchProvider(DoubaoProperties properties,
                                   @Value("${ai.doubao.web-search-enabled:${DOUBAO_WEB_SEARCH_ENABLED:false}}") boolean webSearchEnabled) {
        this.properties = properties;
        this.webSearchEnabled = webSearchEnabled;
    }

    @Override
    public String name() {
        return "doubao-web-search";
    }

    @Override
    public boolean available() {
        return webSearchEnabled && properties.isEnabled() && properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    @Override
    public List<ReferenceCandidate> search(ReferenceSearchQuery query) {
        // Ark chat completions in this project do not expose a documented web-search tool schema.
        // This provider is kept as the first-class extension point and reports unavailable unless
        // the deployment enables a compatible Doubao web-search adapter.
        return List.of();
    }
}
