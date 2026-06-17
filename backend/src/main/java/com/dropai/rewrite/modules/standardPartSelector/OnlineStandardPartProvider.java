package com.dropai.rewrite.modules.standardPartSelector;

import java.util.Optional;

public interface OnlineStandardPartProvider {
    Optional<StandardPartResult> search(StandardPartQuery query);
    Optional<StandardPartResult> fetchDetail(String partId);
    void cacheResult(StandardPartResult part);
}
