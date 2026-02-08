package staraxis.game.planet.production;

import staraxis.game.planet.city.City;
import staraxis.game.planet.resource.ResourceSite;

import java.util.List;
import java.util.Map;

/**
 * CityProductionCalculator（城市生产力计算器）
 *
 * 根据文档设计专门的生产力计算服务，实现90/10规则喵。
 * 计算基础生产力 = 劳动力生产力 + 自然资源生产力喵。
 * 分配：90%专精生产力 + 10%通用生产力喵。
 */
public class CityProductionCalculator {

    /** 基础每劳动力生产力系数喵。 */
    private double basePowerPerLabor;

    /** 专精分配权重映射，key为专精ID，value为类别分配权重映射喵。 */
    private Map<String, Map<String, Double>> specializationWeights;

    /** 默认专精分配权重（如果未找到具体专精配置）喵。 */
    private static final Map<String, Double> DEFAULT_SPECIALIZATION_WEIGHTS = Map.of(
            "MINING", 0.9,      // 采矿专精：90%分配给采矿
            "FARMING", 0.05,    // 5%分配给农业（最小分配）
            "INDUSTRY", 0.05    // 5%分配给工业（最小分配）
    );

    /**
     * 默认构造函数喵。
     */
    public CityProductionCalculator() {
        this.basePowerPerLabor = 1.0; // 默认值，应从配置加载喵
    }

    /**
     * 构造函数，指定基础每劳动力生产力系数喵。
     *
     * @param basePowerPerLabor 基础每劳动力生产力系数喵。
     */
    public CityProductionCalculator(double basePowerPerLabor) {
        this.basePowerPerLabor = basePowerPerLabor;
    }

    /**
     * 计算城市的基础生产力喵。
     * 公式：基础生产力 = 劳动力生产力 + 自然资源生产力喵。
     *
     * @param city 城市喵。
     * @param developedResources 城市已开发的资源点列表喵。
     * @return 基础生产力值喵。
     */
    public double calculateBaseProductionPower(City city, List<ResourceSite> developedResources) {
        // 1. 计算劳动力生产力
        long laborForce = city.calculateLaborForce();
        double laborPower = laborForce * basePowerPerLabor;

        // 2. 计算自然资源生产力
        double naturalPower = 0.0;
        for (ResourceSite resource : developedResources) {
            if (resource.isDeveloped() && !resource.isLandmark) {
                naturalPower += resource.getEffectiveProductionPower();
            }
        }

        return laborPower + naturalPower;
    }

    /**
     * 分配城市生产力喵。
     * 根据90/10规则：90%专精生产力 + 10%通用生产力喵。
     *
     * @param basePower 基础生产力喵。
     * @param specializationId 城市专精ID喵。
     * @param developedResources 城市已开发的资源点列表喵。
     * @param efficiencyBonuses 效率加成映射喵。
     * @return 生产力分配结果喵。
     */
    public ProductionAllocation allocateProduction(
            double basePower,
            String specializationId,
            List<ResourceSite> developedResources,
            Map<String, Double> efficiencyBonuses
    ) {
        ProductionAllocation allocation = new ProductionAllocation();

        // 1. 计算通用生产力（10%）
        double generalPower = basePower * 0.1;
        allocation.generalPower = generalPower;

        // 2. 计算专精生产力（90%）
        double specializedPower = basePower * 0.9;

        // 3. 根据专精分配专精生产力到具体类别
        Map<String, Double> weights = getSpecializationWeights(specializationId);
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            String category = entry.getKey();
            double weight = entry.getValue();
            double categoryPower = specializedPower * weight;
            allocation.setSpecializedPower(category, categoryPower);
        }

        // 4. 添加自然资源生产力（按类别）
        for (ResourceSite resource : developedResources) {
            if (resource.isDeveloped() && !resource.isLandmark) {
                String category = resource.productionCategory;
                double current = allocation.getNaturalResourcePower(category);
                allocation.setNaturalResourcePower(category, current + resource.getEffectiveProductionPower());
            }
        }

        // 5. 设置效率加成
        if (efficiencyBonuses != null) {
            efficiencyBonuses.forEach(allocation::setEfficiencyBonus);
        }

        return allocation;
    }

    /**
     * 计算城市的完整生产力分配喵。
     * 整合了基础计算和分配步骤喵。
     *
     * @param city 城市喵。
     * @param developedResources 城市已开发的资源点列表喵。
     * @param efficiencyBonuses 效率加成映射喵。
     * @return 生产力分配结果喵。
     */
    public ProductionAllocation calculateCityProduction(
            City city,
            List<ResourceSite> developedResources,
            Map<String, Double> efficiencyBonuses
    ) {
        double basePower = calculateBaseProductionPower(city, developedResources);
        return allocateProduction(basePower, city.specializationId, developedResources, efficiencyBonuses);
    }

    /**
     * 获取指定专精的分配权重喵。
     *
     * @param specializationId 专精ID喵。
     * @return 类别分配权重映射喵。
     */
    private Map<String, Double> getSpecializationWeights(String specializationId) {
        if (specializationWeights != null && specializationWeights.containsKey(specializationId)) {
            return specializationWeights.get(specializationId);
        }

        // 返回默认权重（根据文档中的专精示例）
        switch (specializationId) {
            case "RESEARCH":
                return Map.of(
                        "RESEARCH", 0.9,      // 科研专精：90%科研
                        "INDUSTRY", 0.05,     // 5%工业
                        "ADMINISTRATION", 0.05 // 5%行政
                );
            case "MINING":
                return Map.of(
                        "MINING", 0.9,        // 采矿专精：90%采矿
                        "INDUSTRY", 0.05,     // 5%工业
                        "FARMING", 0.05       // 5%农业
                );
            case "INDUSTRY":
                return Map.of(
                        "INDUSTRY", 0.9,      // 工业专精：90%工业
                        "MINING", 0.05,       // 5%采矿
                        "FARMING", 0.05       // 5%农业
                );
            case "FARMING":
                return Map.of(
                        "FARMING", 0.9,       // 农业专精：90%农业
                        "INDUSTRY", 0.05,     // 5%工业
                        "MINING", 0.05        // 5%采矿
                );
            case "ADMINISTRATION":
                return Map.of(
                        "ADMINISTRATION", 0.9, // 行政专精：90%行政
                        "RESEARCH", 0.05,     // 5%科研
                        "INDUSTRY", 0.05      // 5%工业
                );
            case "MILITARY":
                return Map.of(
                        "MILITARY", 0.9,      // 军工专精：90%军工
                        "INDUSTRY", 0.05,     // 5%工业
                        "MINING", 0.05        // 5%采矿
                );
            default:
                return DEFAULT_SPECIALIZATION_WEIGHTS;
        }
    }

    /**
     * 计算紧急订单履约能力喵。
     * 通用生产力可用于任何类别的紧急订单履约，但单位成本更高喵。
     *
     * @param allocation 生产力分配喵。
     * @param targetCategory 目标类别喵。
     * @return 可用于目标类别的通用生产力转换值（考虑转换效率）喵。
     */
    public double calculateEmergencyProductionCapacity(
            ProductionAllocation allocation,
            String targetCategory
    ) {
        // 通用生产力可转换为任何类别，但效率较低
        // 默认转换效率：70%（体现临时转产的低效率）
        double conversionEfficiency = 0.7;
        return allocation.generalPower * conversionEfficiency;
    }

    /**
     * 计算城市的总有效生产力（按类别）喵。
     *
     * @param city 城市喵。
     * @param developedResources 城市已开发的资源点列表喵。
     * @param efficiencyBonuses 效率加成映射喵。
     * @param category 目标类别喵。
     * @return 该类别的总有效生产力喵。
     */
    public double calculateCategoryEffectivePower(
            City city,
            List<ResourceSite> developedResources,
            Map<String, Double> efficiencyBonuses,
            String category
    ) {
        ProductionAllocation allocation = calculateCityProduction(city, developedResources, efficiencyBonuses);
        return allocation.getTotalEffectivePower(category);
    }

    /**
     * 计算城市的总有效生产力（所有类别）喵。
     *
     * @param city 城市喵。
     * @param developedResources 城市已开发的资源点列表喵。
     * @param efficiencyBonuses 效率加成映射喵。
     * @return 所有类别的总有效生产力喵。
     */
    public double calculateTotalEffectivePower(
            City city,
            List<ResourceSite> developedResources,
            Map<String, Double> efficiencyBonuses
    ) {
        ProductionAllocation allocation = calculateCityProduction(city, developedResources, efficiencyBonuses);
        return allocation.getTotalEffectivePower();
    }

    /**
     * 设置专精分配权重映射喵。
     *
     * @param specializationWeights 专精分配权重映射喵。
     */
    public void setSpecializationWeights(Map<String, Map<String, Double>> specializationWeights) {
        this.specializationWeights = specializationWeights;
    }

    /**
     * 设置基础每劳动力生产力系数喵。
     *
     * @param basePowerPerLabor 基础每劳动力生产力系数喵。
     */
    public void setBasePowerPerLabor(double basePowerPerLabor) {
        this.basePowerPerLabor = basePowerPerLabor;
    }

    /**
     * 获取基础每劳动力生产力系数喵。
     *
     * @return 基础每劳动力生产力系数喵。
     */
    public double getBasePowerPerLabor() {
        return basePowerPerLabor;
    }

    /**
     * 验证专精ID是否有效喵。
     *
     * @param specializationId 专精ID喵。
     * @return 如果是已知专精类型，返回true喵。
     */
    public boolean isValidSpecialization(String specializationId) {
        return List.of("RESEARCH", "MINING", "INDUSTRY", "FARMING", "ADMINISTRATION", "MILITARY")
                .contains(specializationId);
    }

    /**
     * 获取专精显示名称喵。
     *
     * @param specializationId 专精ID喵。
     * @return 专精的友好显示名称喵。
     */
    public String getSpecializationDisplayName(String specializationId) {
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
}