package com.dropai.rewrite.service.writing;

import java.util.List;

public interface ReferenceSearchProvider {
    String name();
    boolean available();
    List<ReferenceCandidate> search(ReferenceSearchQuery query);
}
