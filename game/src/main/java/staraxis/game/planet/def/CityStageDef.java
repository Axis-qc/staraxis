package staraxis.game.planet.def;

/**
 * CityStageDef（城市阶段定义）
 *
 * 城市成长阶段的配置定义，从JSON加载喵。
 * 定义城市不同阶段的属性：规模、住房、升级条件等喵。
 */
public class CityStageDef {

    /** 阶段ID，例如 "OUTPOST"、"SETTLEMENT"、"TOWN"、"CITY"、"MEGALOPOLIS"喵。 */
    public String stageId;

    /** 显示名称，用于UI展示喵。 */
    public String displayName;

    /** 城市规模值喵。 */
    public int cityScale;

    /** 住房系数，相对于人口容量的倍数喵。 */
    public double housingFactor;

    /** 升级到下一阶段所需的最小人口喵。 */
    public long upgradePopulationThreshold;

    /** 升级到下一阶段所需的时间（游戏日）喵。 */
    public long upgradeTimeDays;

    /** 升级成本（资源/货币）喵。 */
    public UpgradeCost[] upgradeCosts;

    /** 基础吸引力加成喵。 */
    public double baseAttractivenessBonus;

    /** 资源发现概率加成喵。 */
    public double resourceDiscoveryBonus;

    /** 描述信息喵。 */
    public String description;

    /**
     * 升级成本定义喵。
     */
    public static class UpgradeCost {
        /** 资源类型ID喵。 */
        public String resourceId;
        /** 所需数量喵。 */
        public double amount;
        /** 是否为一次性成本喵。 */
        public boolean oneTime = true;
    }

    /**
     * 获取住房数量喵。
     *
     * @param populationCap 人口容量喵。
     * @return 住房数量喵。
     */
    public long calculateHousing(long populationCap) {
        return (long) (populationCap * housingFactor);
    }

    /**
     * 获取阶段显示名称喵。
     *
     * @return 阶段显示名称喵。
     */
    public String getDisplayName() {
        if (displayName != null) {
            return displayName;
        }
        switch (stageId) {
            case "OUTPOST":
                return "前哨殖民地";
            case "SETTLEMENT":
                return "定居点";
            case "TOWN":
                return "城镇";
            case "CITY":
                return "城市";
            case "MEGALOPOLIS":
                return "巨型都市";
            default:
                return stageId;
        }
    }

    /**
     * 获取阶段描述信息喵。
     *
     * @return 描述字符串喵。
     */
    public String getDescription() {
        if (description != null) {
            return description;
        }
        return String.format("%s（规模%d，住房系数%.1f）", getDisplayName(), cityScale, housingFactor);
    }

    /**
     * 检查是否为最终阶段喵。
     *
     * @return 如果是"MEGALOPOLIS"或没有下一阶段，返回true喵。
     */
    public boolean isFinalStage() {
        return "MEGALOPOLIS".equals(stageId);
    }

    /**
     * 获取下一阶段ID（简单实现）喵。
     *
     * @return 下一阶段ID，如果是最终阶段返回null喵。
     */
    public String getNextStageId() {
        switch (stageId) {
            case "OUTPOST":
                return "SETTLEMENT";
            case "SETTLEMENT":
                return "TOWN";
            case "TOWN":
                return "CITY";
            case "CITY":
                return "MEGALOPOLIS";
            default:
                return null;
        }
    }

    /**
     * 验证阶段定义是否有效喵。
     *
     * @return 如果必要字段都有值，返回true喵。
     */
    public boolean isValid() {
        return stageId != null && !stageId.isEmpty() && cityScale > 0;
    }
}