package staraxis.game.planet.def;

import java.util.Map;

/**
 * SurfaceRegionTypeDef（地表区域类型定义）
 *
 * 地表区域类型的配置定义，从JSON加载喵。
 * 遵循数据驱动原则，禁止硬编码喵。
 */
public class SurfaceRegionTypeDef {

    /** 区域类型ID，例如 "CONTINENT"、"OCEAN"喵。 */
    public String typeId;

    /** 描述信息喵。 */
    public String description;

    /** 生成权重，用于随机选择喵。 */
    public double weight;

    /** 默认区域占比范围（min, max）喵。 */
    public double[] surfacePercentageRange;

    /** 默认可开发空间比例范围（min, max）喵。 */
    public double[] developableSpaceRatioRange;

    /** 次级地貌配置列表喵。 */
    public SubFeatureDef[] subFeatures;

    /** 名称生成词根（前缀/后缀）喵。 */
    public String[] namePrefixes;
    public String[] nameSuffixes;

    /** 资源发现基础概率喵。 */
    public double baseResourceDiscoveryChance;

    /**
     * 次级地貌定义喵。
     */
    public static class SubFeatureDef {
        /** 地貌类型ID喵。 */
        public String featureTypeId;
        /** 地貌占比范围（min, max）喵。 */
        public double[] percentageRange;
        /** 资源倾向性映射喵。 */
        public Map<String, Double> resourceTendencies;
    }

    /**
     * 获取默认显示名称喵。
     *
     * @return 区域类型的友好显示名称喵。
     */
    public String getDisplayName() {
        switch (typeId) {
            case "CONTINENT":
                return "大陆";
            case "OCEAN":
                return "海洋";
            case "ARCHIPELAGO":
                return "群岛";
            case "POLAR_CAP":
                return "极地冰盖";
            default:
                return description != null ? description : typeId;
        }
    }

    /**
     * 检查是否为大陆类型喵。
     *
     * @return 如果typeId为"CONTINENT"，返回true喵。
     */
    public boolean isContinent() {
        return "CONTINENT".equals(typeId);
    }

    /**
     * 检查是否为海洋类型喵。
     *
     * @return 如果typeId为"OCEAN"，返回true喵。
     */
    public boolean isOcean() {
        return "OCEAN".equals(typeId);
    }

    /**
     * 获取描述信息喵。
     *
     * @return 描述字符串喵。
     */
    public String getDescription() {
        return description != null ? description : getDisplayName();
    }
}