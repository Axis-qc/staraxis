package com.staraxis.universegen.config;

import java.util.ArrayList;
import java.util.List;

/**
 * ConfigValidator 用于在运行前检测 UniverseGenConfig 是否包含非法或极端参数。
 * 若发现错误参数，可选择抛出异常或回退默认值。
 */
public final class ConfigValidator {

    private ConfigValidator() {}

    /**
     * 校验配置，发现非法值抛出 IllegalArgumentException（严格模式）。
     */
    public static void validateStrict(UniverseGenConfig cfg) {
        List<String> issues = detectIssues(cfg);
        if (!issues.isEmpty()) {
            throw new IllegalArgumentException("UniverseGenConfig invalid: " + String.join("; ", issues));
        }
    }

    /**
     * 尝试修正极端/非法值，返回修正后的配置副本。
     */
    public static UniverseGenConfig sanitize(UniverseGenConfig cfg) {
        UniverseGenConfig copy = cloneConfig(cfg);
        // sectorCount 为派生值（由 galaxyRadiusR 计算），此处不直接 setSectorCount。
        if (copy.getSectorCount() <= 0) copy.setGalaxyRadiusR(0);
        if (copy.getHexRadiusLy() <= 0) copy.setHexRadiusLy(5f);
        double ratio = copy.getStarToDeepSpaceRatio();
        if (ratio <= 0 || ratio > 1) copy.setStarToDeepSpaceRatio(0.5);
        return copy;
    }

    private static List<String> detectIssues(UniverseGenConfig cfg) {
        List<String> issues = new ArrayList<>();
        if (cfg.getSectorCount() <= 0) issues.add("sectorCount<=0");
        if (cfg.getHexRadiusLy() <= 0) issues.add("hexRadiusLy<=0");
        double ratio = cfg.getStarToDeepSpaceRatio();
        if (ratio <= 0 || ratio > 1) issues.add("starToDeepSpaceRatio not in (0,1]");
        return issues;
    }

    private static UniverseGenConfig cloneConfig(UniverseGenConfig cfg) {
        UniverseGenConfig c = new UniverseGenConfig();
        c.setSeed(cfg.getSeed());
        c.setGalaxyRadiusR(cfg.getGalaxyRadiusR());
        c.setHexRadiusLy(cfg.getHexRadiusLy());
        c.setStarToDeepSpaceRatio(cfg.getStarToDeepSpaceRatio());
        return c;
    }
}
