package com.dropai.rewrite.service.ppt;

import org.springframework.stereotype.Service;
import java.nio.file.Path;

/** Public boundary for the final two frozen stages. Upstream services supply the mapped tree. */
@Service
public class PptEngineV1Service {
    private final PptLayoutPlannerV1 layouts;private final PptRendererV1 renderer;
    public PptEngineV1Service(PptLayoutPlannerV1 layouts,PptRendererV1 renderer){this.layouts=layouts;this.renderer=renderer;}
    public PptRendererV1.RenderResult generate(PptAssetMapperV1.MappingResult mappedTree,Path output,Path report)throws Exception{return renderer.render(layouts.plan(mappedTree),output,report);}
}
