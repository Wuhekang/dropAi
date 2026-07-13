package com.dropai.rewrite.service.writing;

import java.util.List;

public interface ReferenceSearchProvider {
    String name();
    boolean available();
    List<ReferenceCandidate> search(ReferenceSearchQuery query);

    default String providerCode() {
        return name();
    }

    default String providerName() {
        return name();
    }

    default boolean supportsLanguage(String language) {
        return true;
    }

    default ProviderHealthStatus healthCheck() {
        return ProviderHealthStatus.of(providerCode(), available(), available(), available(),
                "PUBLIC", "", available() ? "AVAILABLE" : "UNAVAILABLE", available() ? "OK" : "Provider is not available", 0);
    }

    default ReferenceVerificationResult verify(ReferenceCandidate candidate) {
        boolean ok = candidate != null && candidate.basicallyVerified();
        return new ReferenceVerificationResult(ok, ok ? candidate.verificationStatus() : "UNVERIFIED",
                ok ? "metadata fields are present" : "missing title, authors, year, or source metadata");
    }
}
