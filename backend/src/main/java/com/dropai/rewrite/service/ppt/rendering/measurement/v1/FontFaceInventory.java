package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import java.util.List;

@FunctionalInterface
public interface FontFaceInventory {
    List<FontFaceResource> find(String family, int weight);
}
