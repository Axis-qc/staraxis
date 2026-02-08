package staraxis.game.planet.def;

/**
 * ResourceTypeDef（资源类型定义）
 *
 * 自然资源类型的配置定义，从JSON加载喵。
 * 定义资源点的属性：生产力类别、发现概率、开发收益等喵。
 */
public class ResourceTypeDef {

    /** 资源类型ID，例如 "MINERAL"、"AGRICULTURE"、"FISHERY"、"FOREST"、"LANDSCAPE"喵。 */
    public String resourceId;

    /** 显示名称，用于UI展示喵。 */
    public String displayName;

    /** 描述信息喵。 */
    public String description;

    /** 生产力类别，例如 "MINING"、"FARMING"、"INDUSTRY"等喵。 */
    public String productionCategory;

    /** 是否为景观资源（不提供生产力，只提供吸引力加成）喵。 */
    public boolean isLandmark = false;

    /** 基础生产力值范围（min, max）喵。 */
    public double[] productionPowerRange;

    /** 吸引力加成范围（min, max），景观资源使用喵。 */
    public double[] attractivenessBonusRange;

    /** 发现权重，影响在地表区域中的生成概率喵。 */
    public double discoveryWeight;

    /** 规模等级配置喵。 */
    public SizeTierDef[] sizeTiers;

    /** 开发时间（游戏日）喵。 */
    public long developmentTimeDays;

    /** 开发成本（资源/货币）喵。 */
    public DevelopmentCost[] developmentCosts;

    /** 资源图标资源路径喵。 */
    public String iconPath;

    /** 资源颜色（十六进制）喵。 */
    public String colorHex;

    /**
     * 规模等级定义喵。
     */
    public static class SizeTierDef {
        /** 等级ID，例如 "SMALL"、"MEDIUM"、"LARGE"、"RICH"喵。 */
        public String tierId;
        /** 显示名称喵。 */
        public String displayName;
        /** 生产力乘数喵。 */
        public double productionMultiplier;
        /** 吸引力加成乘数（景观用）喵。 */
        public double attractivenessMultiplier;
        /** 生成权重喵。 */
        public double weight;
        /** 发现难度修正喵。 */
        public double discoveryDifficulty;
    }

    /**
     * 开发成本定义喵。
     */
    public static class DevelopmentCost {
        /** 资源类型ID喵。 */
        public String resourceId;
        /** 所需数量喵。 */
        public double amount;
        /** 成本描述喵。 */
        public String description;
    }

    /**
     * 获取资源显示名称喵。
     *
     * @return 资源显示名称喵。
     */
    public String getDisplayName() {
        if (displayName != null) {
            return displayName;
        }
        switch (resourceId) {
            case "MINERAL":
                return "矿产";
            case "AGRICULTURE":
                return "农业";
            case "FISHERY":
                return "渔业";
            case "FOREST":
                return "林业";
            case "LANDSCAPE":
                return "景观";
            case "ENERGY":
                return "能源";
            default:
                return resourceId;
        }
    }

    /**
     * 获取生产力类别显示名称喵。
     *
     * @return 生产力类别显示名称喵。
     */
    public String getProductionCategoryDisplayName() {
        if (productionCategory == null) {
            return "无";
        }
        switch (productionCategory) {
            case "MINING":
                return "采矿";
            case "FARMING":
                return "农业";
            case "INDUSTRY":
                return "工业";
            case "RESEARCH":
                return "科研";
            case "ADMINISTRATION":
                return "行政";
            case "MILITARY":
                return "军工";
            default:
                return productionCategory;
        }
    }

    /**
     * 获取资源描述信息喵。
     *
     * @return 描述字符串喵。
     */
    public String getDescription() {
        if (description != null) {
            return description;
        }
        if (isLandmark) {
            return String.format("%s（景观资源，提供吸引力加成）", getDisplayName());
        } else {
            return String.format("%s（提供%s生产力）", getDisplayName(), getProductionCategoryDisplayName());
        }
    }

    /**
     * 检查是否为矿产资源喵。
     *
     * @return 如果resourceId为"MINERAL"，返回true喵。
     */
    public boolean isMineralResource() {
        return "MINERAL".equals(resourceId);
    }

    /**
     * 检查是否为农业资源喵。
     *
     * @return 如果resourceId为"AGRICULTURE"，返回true喵。
     */
    public boolean isAgricultureResource() {
        return "AGRICULTURE".equals(resourceId);
    }

    /**
     * 检查是否为景观资源喵。
     *
     * @return 如果isLandmark为true，返回true喵。
     */
    public boolean isLandmarkResource() {
        return isLandmark;
    }

    /**
     * 获取指定规模等级的定义喵。
     *
     * @param tierId 等级ID喵。
     * @return 规模等级定义，如果不存在返回null喵。
     */
    public SizeTierDef getSizeTier(String tierId) {
        if (sizeTiers == null) {
            return null;
        }
        for (SizeTierDef tier : sizeTiers) {
            if (tier.tierId.equals(tierId)) {
                return tier;
            }
        }
        return null;
    }

    /**
     * 获取默认规模等级（第一个或权重最高的）喵。
     *
     * @return 默认规模等级定义喵。
     */
    public SizeTierDef getDefaultSizeTier() {
        if (sizeTiers == null || sizeTiers.length == 0) {
            return null;
        }
        // 返回第一个等级作为默认
        return sizeTiers[0];
    }

    /**
     * 验证资源类型定义是否有效喵。
     *
     * @return 如果必要字段都有值，返回true喵。
     */
    public boolean isValid() {
        if (resourceId == null || resourceId.isEmpty()) {
            return false;
        }
        if (isLandmark) {
            // 景观资源需要吸引力加成范围
            return attractivenessBonusRange != null && attractivenessBonusRange.length == 2;
        } else {
            // 普通资源需要生产力范围和类别
            return productionPowerRange != null && productionPowerRange.length == 2 &&
                    productionCategory != null && !productionCategory.isEmpty();
        }
    }

    /**
     * 获取规模等级显示名称喵。
     *
     * @param tierId 等级ID喵。
     * @return 等级显示名称喵。
     */
    public String getSizeTierDisplayName(String tierId) {
        SizeTierDef tier = getSizeTier(tierId);
        if (tier != null && tier.displayName != null) {
            return tier.displayName;
        }
        switch (tierId) {
            case "SMALL":
                return "小型";
            case "MEDIUM":
                return "中型";
            case "LARGE":
                return "大型";
            case "RICH":
                return "富饶";
            default:
                return tierId;
        }
    }
}