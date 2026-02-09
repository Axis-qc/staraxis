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
     * 获取显示名称的 i18n Key 喵。
     */
    public String nameKey;

    /**
     * 获取描述信息的 i18n Key 喵。
     */
    public String descriptionKey;

    /**
     * 随机生成名称时使用的命名池 ID 喵。
     */
    public String namePoolId;

    /**
     * 获取区域类型的友好显示名称（通过 i18n 或 typeId）喵。
     *
     * @return 区域类型的友好显示名称喵。
     */
    public String getDisplayName() {
        // Def 层不做翻译，避免返回 i18n key 导致展示层误用喵
        // 若需要展示文本，应由上层通过 i18n 系统解析 nameKey 喵
        if (description != null && !description.isBlank()) {
            return description;
        }
        return typeId;
    }

    /**
     * 获取描述信息喵。
     *
     * @return 描述字符串喵。
     */
    public String getDescription() {
        return descriptionKey != null ? descriptionKey : getDisplayName();
    }
}