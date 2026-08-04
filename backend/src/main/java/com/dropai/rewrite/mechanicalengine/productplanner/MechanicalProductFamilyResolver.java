package com.dropai.rewrite.mechanicalengine.productplanner;

import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class MechanicalProductFamilyResolver {
    public ProductFamily resolve(String requirement) {
        if (requirement == null || requirement.isBlank()) throw new IllegalArgumentException("Mechanical requirement cannot be empty");
        String value = requirement.toLowerCase(Locale.ROOT);
        if (contains(value, "夹具", "夹持", "clamp", "fixture")) return ProductFamily.FIXTURE;
        if (contains(value, "agv", "搬运车", "无人运输", "automated guided")) return ProductFamily.AGV;
        if (contains(value, "机器人", "robot", "巡检", "检测机器人")) return ProductFamily.ROBOT;
        if (contains(value, "输送", "传送带", "conveyor")) return ProductFamily.CONVEYOR;
        if (contains(value, "机构", "mechanism", "连杆", "凸轮", "减速")) return ProductFamily.MECHANISM;
        throw new IllegalArgumentException("UNSUPPORTED_MECHANICAL_PRODUCT");
    }

    private boolean contains(String value, String... keys) {
        for (String key : keys) if (value.contains(key)) return true;
        return false;
    }
}
