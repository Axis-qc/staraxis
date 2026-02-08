package staraxis.game.planet.surface;

import java.util.Map;

/**
 * SubSurfaceFeature（次级地貌）
 *
 * 区域内部的地貌构成，影响资源发现倾向喵。
 * 每个地表区域内部可细分为若干"次级地貌"，用于影响资源发现倾向喵。
 */
public class SubSurfaceFeature {

    /** 地貌类型：MOUNTAINS（山脉）、PLAINS（平原）、SHALLOW_SEA（浅海）、DEEP_SEA（深海）等喵。 */
    public String featureType;

    /** 地貌占比（0-1），该地貌占其所属地表区域面积的百分比喵。 */
    public double percentageOfRegion;

    /** 资源倾向性映射，key为资源类型ID，value为发现权重喵。 */
    public Map<String, Double> resourceTendencies;

    /**
     * 默认构造函数喵。
     */
    public SubSurfaceFeature() {
    }

    /**
     * 获取地貌类型显示名称喵。
     *
     * @return 地貌类型的友好显示名称喵。
     */
    public String getDisplayFeatureType() {
        switch (featureType) {
            case "MOUNTAINS":
                return "山脉";
            case "PLAINS":
                return "平原";
            case "SHALLOW_SEA":
                return "浅海";
            case "DEEP_SEA":
                return "深海";
            case "HILLS":
                return "丘陵";
            case "FOREST":
                return "森林";
            case "DESERT":
                return "沙漠";
            case "TUNDRA":
                return "苔原";
            case "VOLCANIC":
                return "火山";
            default:
                return featureType;
        }
    }

    /**
     * 获取指定资源类型的发现权重喵。
     *
     * @param resourceTypeId 资源类型ID喵。
     * @return 发现权重，如果没有该资源的倾向性则返回0喵。
     */
    public double getResourceTendency(String resourceTypeId) {
        if (resourceTendencies == null) {
            return 0.0;
        }
        return resourceTendencies.getOrDefault(resourceTypeId, 0.0);
    }

    /**
     * 检查该地貌是否适合某种资源类型喵。
     *
     * @param resourceTypeId 资源类型ID喵。
     * @param threshold 阈值喵。
     * @return 如果发现权重大于阈值，返回true喵。
     */
    public boolean isSuitableForResource(String resourceTypeId, double threshold) {
        return getResourceTendency(resourceTypeId) > threshold;
    }

    /**
     * 获取地貌描述信息喵。
     *
     * @return 包含地貌类型和占比的描述字符串喵。
     */
    public String getDescription() {
        return String.format("%s (%.1f%%)", getDisplayFeatureType(), percentageOfRegion * 100);
    }
}