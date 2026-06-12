package com.dropai.rewrite.modules.structureEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StructureEngine {
    public DesignProject design(DesignProject project) {
        double l = project.number("总长", 4200), w = project.number("总宽", 1600), h = project.number("总高", 1800);
        String material = text(project, "材料", "Q235B");
        List<DesignProject.Component> parts = new ArrayList<>();
        add(parts, "BODY", "主体壳体", "形成主要工作空间并承载功能单元", material, 1, .12, .18, .22, .68, .64, .55, true, l, w, h);
        add(parts, "BASE", "安装底座", "承载设备并与基础连接", material, 1, .08, .10, .05, .76, .80, .10, true, l, w, h);
        add(parts, "SUPPORT", "支撑组件", "传递载荷并保证总体稳定性", material, 4, .18, .16, .10, .08, .10, .22, true, l, w, h);
        add(parts, "INTERFACE", "入口接口组件", "连接上游系统并导入介质或物料", material, 1, .02, .32, .35, .12, .26, .22, true, l, w, h);
        add(parts, "INTERFACE", "出口接口组件", "连接下游系统并导出介质或物料", material, 1, .86, .32, .35, .12, .26, .22, true, l, w, h);
        add(parts, "MAINTENANCE", "检修门组件", "提供内部检查、维护和清理通道", material, 1, .40, .17, .32, .18, .02, .22, true, l, w, h);
        add(parts, "CONNECTION", "连接与紧固组件", "连接主体、底座和功能单元", "标准件", 1, .22, .18, .20, .56, .03, .04, false, l, w, h);

        for (String function : project.getMainFunctions()) {
            if (parts.stream().noneMatch(p -> function.equals(p.getFunction()))) {
                add(parts, "FUNCTION", function + "功能单元", function, material, 1, .30, .26, .28, .40, .48, .38, true, l, w, h);
            }
        }
        project.setComponents(parts);
        project.setBom(parts.stream().map(p -> new DesignProject.BomItem(
                p.getSequence(), p.getName(), p.getMaterial(), p.getQuantity(), p.getFunction())).toList());
        project.setDimensionChains(List.of(
                new DesignProject.DimensionChain("总长", l, "mm", "整机"),
                new DesignProject.DimensionChain("总宽", w, "mm", "整机"),
                new DesignProject.DimensionChain("总高", h, "mm", "整机"),
                new DesignProject.DimensionChain("安装孔中心距", l * .72, "mm", "安装底座"),
                new DesignProject.DimensionChain("接口中心高", h * .46, "mm", "接口组件"),
                new DesignProject.DimensionChain("支撑跨距", l * .64, "mm", "支撑组件")));
        project.setTechnicalRequirements(List.of(
                "图纸尺寸与统一设计参数表保持一致，修改参数后须重新校核。",
                "焊接件完成后清理焊渣和毛刺，关键安装面校正后加工。",
                "装配后检查接口位置、支撑稳定性和检修空间。",
                "未注材料、标准件和制造公差须由设计人员确认。"));
        return project;
    }

    private void add(List<DesignProject.Component> list, String role, String name, String function, String material, int qty,
                     double x, double y, double z, double l, double w, double h, boolean key, double totalL, double totalW, double totalH) {
        list.add(new DesignProject.Component(list.size() + 1, role, name, function, material, qty,
                x * totalL, y * totalW, z * totalH, l * totalL, w * totalW, h * totalH, key));
    }
    private String text(DesignProject project, String name, String fallback) {
        return project.allParameters().stream().filter(p -> name.equals(p.getName())).map(p -> String.valueOf(p.getValue())).findFirst().orElse(fallback);
    }
}
