package com.dropai.rewrite.modules.unknownPartResolver;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UnknownPartResolver {
    public DesignProject resolve(DesignProject project) {
        List<DesignProject.DesignPart> result = new ArrayList<>();
        for (DesignProject.DesignPart part : project.getResolvedParts()) {
            if ("unresolved".equals(part.getPartType())) result.add(generate(part));
            else result.add(part);
        }
        project.setResolvedParts(result);
        project.setDetailFeatures(result.stream().flatMap(p -> p.getGeometryFeatures().stream()).distinct().toList());
        project.setMaterials(result.stream().map(DesignProject.DesignPart::getMaterial).filter(v -> v != null && !v.isBlank()).distinct().toList());
        return project;
    }

    private DesignProject.DesignPart generate(DesignProject.DesignPart input) {
        DesignProject.DesignPart part = new DesignProject.DesignPart();
        part.setPartType("non_standard");
        part.setName(input.getName());
        part.setParentStructure(input.getParentStructure());
        part.setGeneratedBy("rule-based-geometry-synthesizer");
        part.setSource("StructureTreeBuilder");
        part.setQuantity(Math.max(1, input.getQuantity()));
        part.setMaterial(material(input.getName()));
        part.setProcess(process(input.getName()));
        part.setGeometryFeatures(features(input.getName()));
        part.setFeatureTree(features(input.getName()));
        part.setModelingMethod("feature_based_parametric");
        return part;
    }

    private List<String> features(String name) {
        if (name.contains("磁") || name.contains("吸附")) return List.of("安装板", "磁体槽", "沉头固定孔", "防护盖", "限位倒角");
        if (name.contains("检测") || name.contains("传感") || name.contains("导轨") || name.contains("滑轨")) return List.of("支架板", "导轨安装孔", "传感器腰形孔", "调节刻度槽", "加强折边");
        if (name.contains("刷") || name.contains("清扫")) return List.of("圆盘", "刷毛阵列", "中心轴孔", "压紧盖板", "平衡孔");
        if (name.contains("履带")) return List.of("环形带体", "内侧导向齿", "张紧槽", "加强层", "防滑纹");
        if (name.contains("机架") || name.contains("支架") || name.contains("底座") || name.contains("侧板") || name.contains("主板")) return List.of("主板", "折弯边", "安装孔阵列", "加强筋", "定位孔");
        if (name.contains("外壳") || name.contains("防护") || name.contains("盖板") || name.contains("电池舱")) return List.of("薄板罩壳", "圆角折边", "检修盖", "散热孔", "密封槽");
        if (name.contains("灰斗") || name.contains("导流") || name.contains("检修") || name.contains("观察") || name.contains("扩散")) return List.of("板焊结构", "折弯边", "焊接坡口", "安装孔", "密封面");
        if (name.contains("磁") || name.contains("吸附")) return List.of("安装板", "磁体槽", "沉头固定孔", "防护盖", "限位倒角");
        if (name.contains("检测") || name.contains("传感")) return List.of("支架板", "导轨安装孔", "传感器腰形孔", "调节刻度槽", "加强折边");
        if (name.contains("刷") || name.contains("清扫")) return List.of("圆盘", "刷毛阵列", "中心轴孔", "压紧盖板", "平衡孔");
        if (name.contains("履带")) return List.of("环形带体", "内侧导向齿", "张紧槽", "加强层", "防滑纹");
        if (name.contains("机架") || name.contains("支架") || name.contains("底座")) return List.of("主板", "折弯边", "安装孔阵列", "加强筋", "定位孔");
        if (name.contains("外壳") || name.contains("防护")) return List.of("薄板罩壳", "圆角折边", "检修盖", "散热孔", "密封槽");
        return List.of("基体", "安装孔", "定位面", "加强筋", "倒角圆角");
    }

    private String material(String name) {
        if (name.contains("刷")) return "尼龙刷丝+铝合金";
        if (name.contains("履带")) return "耐磨橡胶";
        if (name.contains("外壳") || name.contains("防护") || name.contains("电池舱")) return "ABS+铝板";
        if (name.contains("检测") || name.contains("机架") || name.contains("支架") || name.contains("侧板") || name.contains("主板")) return "6061铝合金";
        if (name.contains("磁")) return "钕铁硼磁钢+Q235B";
        if (name.contains("灰斗") || name.contains("箱体") || name.contains("导流") || name.contains("检修")) return "Q235B";
        if (name.contains("刷")) return "尼龙刷丝+铝合金";
        if (name.contains("履带")) return "耐磨橡胶";
        if (name.contains("外壳") || name.contains("防护")) return "ABS+铝板";
        if (name.contains("检测") || name.contains("机架") || name.contains("支架")) return "6061铝合金";
        if (name.contains("磁")) return "钕铁硼磁钢+Q235B";
        return "Q235B";
    }

    private String process(String name) {
        if (name.contains("外壳") || name.contains("机架") || name.contains("支架") || name.contains("侧板") || name.contains("主板")) return "板材下料、折弯、钻孔、装配";
        if (name.contains("轮") || name.contains("轴")) return "车削、钻孔、表面处理";
        if (name.contains("灰斗") || name.contains("箱体") || name.contains("导流")) return "板材下料、折弯、焊接、打磨、防腐";
        if (name.contains("外壳") || name.contains("机架") || name.contains("支架")) return "板材下料、折弯、钻孔、装配";
        if (name.contains("轴") || name.contains("轮")) return "车削、钻孔、表面处理";
        return "下料、钻孔、倒角、装配";
    }
}
