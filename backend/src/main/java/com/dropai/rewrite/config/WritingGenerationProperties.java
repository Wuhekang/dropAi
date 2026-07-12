package com.dropai.rewrite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "writing")
public class WritingGenerationProperties {
    private ReferenceSearch referenceSearch = new ReferenceSearch();
    private int maxRetry = 3;
    private int chapterTimeoutSeconds = 180;

    public ReferenceSearch getReferenceSearch() {
        return referenceSearch;
    }

    public void setReferenceSearch(ReferenceSearch referenceSearch) {
        this.referenceSearch = referenceSearch;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
    }

    public int getChapterTimeoutSeconds() {
        return chapterTimeoutSeconds;
    }

    public void setChapterTimeoutSeconds(int chapterTimeoutSeconds) {
        this.chapterTimeoutSeconds = chapterTimeoutSeconds;
    }

    public static class ReferenceSearch {
        private boolean enabled = true;
        private String provider = "doubao,openalex,crossref";
        private int maxResults = 30;
        private int timeoutSeconds = 30;
        private int retryCount = 3;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public void setRetryCount(int retryCount) {
            this.retryCount = retryCount;
        }

        public List<String> providerOrder() {
            if (provider == null || provider.isBlank()) return List.of("openalex", "crossref");
            List<String> result = new ArrayList<>();
            Arrays.stream(provider.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(String::toLowerCase)
                    .forEach(result::add);
            return result.isEmpty() ? List.of("openalex", "crossref") : result;
        }
    }
}
