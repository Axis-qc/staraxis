package staraxis.game.planet.def;

import java.util.Map;

/**
 * CitySpecializationDef（城市专精定义）
 *
 * 城市专精方向的配置定义，从JSON加载喵。
 * 定义专精对应的生产力分配权重、效率加成、UI显示信息等喵。
 */
public class CitySpecializationDef {

    /** 专精ID，例如 "RESEARCH"、"MINING"、"INDUSTRY"、"FARMING"、"ADMINISTRATION"、"MILITARY"喵。 */
    public String specializationId;

    /** 显示名称，用于UI展示喵。 */
    public String displayName;

    /** 描述信息喵。 */
    public String description;

    /** 生产力分配权重映射，key为生产力类别，value为分配比例（0-1，所有类别之和应为1.0）喵。 */
    public Map<String, Double> productionAllocationWeights;

    /** 效率加成映射，key为生产力类别，value为加成系数（例如1.15表示+15%加成）喵。 */
    public Map<String, Double> efficiencyBonuses;

    /** 专精重置成本（资源/货币）喵。 */
    public ResetCost[] resetCosts;

    /** 专精图标资源路径喵。 */
    public String iconPath;

    /** 专精颜色（十六进制）喵。 */
    public String colorHex;

    /** 是否为默认专精喵。 */
    public boolean isDefault = false;

    /** 专精类别（用于分组）喵。 */
    public String category;

    /**
     * 重置成本定义喵。
     */
    public static class ResetCost {
        /** 资源类型ID喵。 */
        public String resourceId;
        /** 所需数量喵。 */
        public double amount;
        /** 成本描述喵。 */
        public String description;
    }

    /**
     * 获取专精显示名称喵。
     *
     * @return 专精显示名称喵。
     */
    public String getDisplayName() {
        if (displayName != null) {
            return displayName;
        }
        switch (specializationId) {
            case "RESEARCH":
                return "科研专精";
            case "MINING":
                return "采矿专精";
            case "INDUSTRY":
                return "工业专精";
            case "FARMING":
                return "农业专精";
            case "ADMINISTRATION":
                return "行政专精";
            case "MILITARY":
                return "军工专精";
            default:
                return specializationId;
        }
    }

    /**
     * 获取指定类别的分配权重喵。
     *
     * @param category 生产力类别喵。
     * @return 分配权重，如果没有配置返回0喵。
     */
    public double getAllocationWeight(String category) {
        if (productionAllocationWeights == null) {
            return 0.0;
        }
        return productionAllocationWeights.getOrDefault(category, 0.0);
    }

    /**
     * 获取指定类别的效率加成喵。
     *
     * @param category 生产力类别喵。
     * @return 效率加成系数，如果没有配置返回1.0喵。
     */
    public double getEfficiencyBonus(String category) {
        if (efficiencyBonuses == null) {
            return 1.0;
        }
        return efficiencyBonuses.getOrDefault(category, 1.0);
    }

    /**
     * 获取主要生产力类别（分配权重最高的类别）喵。
     *
     * @return 主要生产力类别ID喵。
     */
    public String getPrimaryProductionCategory() {
        if (productionAllocationWeights == null || productionAllocationWeights.isEmpty()) {
            return null;
        }
        return productionAllocationWeights.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * 验证专精定义是否有效喵。
     *
     * @return 如果必要字段都有值且分配权重总和接近1.0，返回true喵。
     */
    public boolean isValid() {
        if (specializationId == null || specializationId.isEmpty()) {
            return false;
        }
        if (productionAllocationWeights == null || productionAllocationWeights.isEmpty()) {
            return false;
        }
        // 检查分配权重总和是否合理（接近1.0，允许微小误差）
        double sum = productionAllocationWeights.values().stream().mapToDouble(Double::doubleValue).sum();
        return Math.abs(sum - 1.0) < 0.01;
    }

    /**
     * 获取专精描述信息喵。
     *
     * @return 描述字符串喵。
     */
    public String getDescription() {
        if (description != null) {
            return description;
        }
        String primaryCat = getPrimaryProductionCategory();
        if (primaryCat != null) {
            return String.format("%s：专注于%s生产", getDisplayName(), getCategoryDisplayName(primaryCat));
        }
        return getDisplayName();
    }

    /**
     * 获取生产力类别显示名称喵。
     *
     * @param category 生产力类别ID喵。
     * @return 类别显示名称喵。
     */
    private String getCategoryDisplayName(String category) {
        switch (category) {
            case "RESEARCH":
                return "科研";
            case "MINING":
                return "采矿";
            case "INDUSTRY":
                return "工业";
            case "FARMING":
                return "农业";
            case "ADMINISTRATION":
                return "行政";
            case "MILITARY":
                return "军工";
            default:
                return category;
        }
    }

    /**
     * 检查是否为科研专精喵。
     *
     * @return 如果specializationId为"RESEARCH"，返回true喵。
     */
    public boolean isResearchSpecialization() {
        return "RESEARCH".equals(specializationId);
    }

    /**
     * 检查是否为军事专精喵。
     *
     * @return 如果specializationId为"MILITARY"，返回true喵。
     */
    public boolean isMilitarySpecialization() {
        return "MILITARY".equals(specializationId);
    }

    /**
     * 检查是否为生产型专精（采矿/工业/农业）喵。
     *
     * @return 如果是生产型专精，返回true喵。
     */
    public boolean isProductionSpecialization() {
        return "MINING".equals(specializationId) || "INDUSTRY".equals(specializationId) || "FARMING".equals(specializationId);
    }
}