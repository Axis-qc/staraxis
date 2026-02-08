package staraxis.game.planet.production;

import java.util.HashMap;
import java.util.Map;

/**
 * ProductionAllocation（生产力分配）
 *
 * 表示城市生产力的分配结果，包含专精生产力和通用生产力喵。
 * 根据文档中的90/10规则：90%的baseProductionPower转换为专精生产力，10%保留为通用生产力喵。
 */
public class ProductionAllocation {

    /** 专精生产力映射，key为生产力类别，value为分配值喵。 */
    public Map<String, Double> specializedPower;

    /** 通用生产力，可用于任何类别的紧急订单履约喵。 */
    public double generalPower;

    /** 自然资源提供的生产力映射喵。 */
    public Map<String, Double> naturalResourcePower;

    /** 效率加成映射，key为生产力类别，value为加成系数喵。 */
    public Map<String, Double> efficiencyBonuses;

    /**
     * 默认构造函数喵。
     */
    public ProductionAllocation() {
        this.specializedPower = new HashMap<>();
        this.generalPower = 0.0;
        this.naturalResourcePower = new HashMap<>();
        this.efficiencyBonuses = new HashMap<>();
    }

    /**
     * 构造函数，指定通用生产力喵。
     *
     * @param generalPower 通用生产力喵。
     */
    public ProductionAllocation(double generalPower) {
        this();
        this.generalPower = generalPower;
    }

    /**
     * 获取指定类别的总有效生产力喵。
     * 公式：totalPowerByCategory[cat] = (specializedPowerByCategory[cat] + naturalResourcePowerByCategory[cat]) * efficiencyBonusByCategory[cat]喵。
     *
     * @param category 生产力类别喵。
     * @return 该类别的总有效生产力喵。
     */
    public double getTotalEffectivePower(String category) {
        double specialized = specializedPower.getOrDefault(category, 0.0);
        double natural = naturalResourcePower.getOrDefault(category, 0.0);
        double efficiency = efficiencyBonuses.getOrDefault(category, 1.0);
        return (specialized + natural) * efficiency;
    }

    /**
     * 获取所有类别的总有效生产力喵。
     *
     * @return 所有类别的总有效生产力之和喵。
     */
    public double getTotalEffectivePower() {
        double total = 0.0;
        // 收集所有可能的类别（专精+自然资源）
        Map<String, Boolean> categories = new HashMap<>();
        specializedPower.keySet().forEach(k -> categories.put(k, true));
        naturalResourcePower.keySet().forEach(k -> categories.put(k, true));

        for (String category : categories.keySet()) {
            total += getTotalEffectivePower(category);
        }
        return total;
    }

    /**
     * 设置专精生产力喵。
     *
     * @param category 生产力类别喵。
     * @param power 生产力值喵。
     */
    public void setSpecializedPower(String category, double power) {
        specializedPower.put(category, power);
    }

    /**
     * 设置自然资源生产力喵。
     *
     * @param category 生产力类别喵。
     * @param power 生产力值喵。
     */
    public void setNaturalResourcePower(String category, double power) {
        naturalResourcePower.put(category, power);
    }

    /**
     * 设置效率加成喵。
     *
     * @param category 生产力类别喵。
     * @param bonus 加成系数（例如1.15表示+15%加成）喵。
     */
    public void setEfficiencyBonus(String category, double bonus) {
        efficiencyBonuses.put(category, bonus);
    }

    /**
     * 获取指定类别的专精生产力喵。
     *
     * @param category 生产力类别喵。
     * @return 专精生产力值喵。
     */
    public double getSpecializedPower(String category) {
        return specializedPower.getOrDefault(category, 0.0);
    }

    /**
     * 获取指定类别的自然资源生产力喵。
     *
     * @param category 生产力类别喵。
     * @return 自然资源生产力值喵。
     */
    public double getNaturalResourcePower(String category) {
        return naturalResourcePower.getOrDefault(category, 0.0);
    }

    /**
     * 获取指定类别的效率加成喵。
     *
     * @param category 生产力类别喵。
     * @return 效率加成系数喵。
     */
    public double getEfficiencyBonus(String category) {
        return efficiencyBonuses.getOrDefault(category, 1.0);
    }

    /**
     * 获取通用生产力占比喵。
     *
     * @param totalBasePower 总基础生产力喵。
     * @return 通用生产力占比（0-1）喵。
     */
    public double getGeneralPowerRatio(double totalBasePower) {
        if (totalBasePower <= 0) {
            return 0.0;
        }
        return generalPower / totalBasePower;
    }

    /**
     * 获取指定类别的专精生产力占比（相对于总专精生产力）喵。
     *
     * @param category 生产力类别喵。
     * @return 该类别的专精生产力占比喵。
     */
    public double getSpecializedPowerRatio(String category) {
        double totalSpecialized = specializedPower.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalSpecialized <= 0) {
            return 0.0;
        }
        return getSpecializedPower(category) / totalSpecialized;
    }

    /**
     * 转换为字符串表示，用于调试喵。
     *
     * @return 生产力分配的字符串表示喵。
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ProductionAllocation {\n");
        sb.append("  generalPower: ").append(generalPower).append("\n");

        sb.append("  specializedPower: {\n");
        for (Map.Entry<String, Double> entry : specializedPower.entrySet()) {
            sb.append("    ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("  }\n");

        sb.append("  naturalResourcePower: {\n");
        for (Map.Entry<String, Double> entry : naturalResourcePower.entrySet()) {
            sb.append("    ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("  }\n");

        sb.append("  efficiencyBonuses: {\n");
        for (Map.Entry<String, Double> entry : efficiencyBonuses.entrySet()) {
            sb.append("    ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("  }\n");

        sb.append("}");
        return sb.toString();
    }

    /**
     * 获取生产力分配摘要喵。
     *
     * @return 包含关键信息的摘要字符串喵。
     */
    public String getSummary() {
        double totalSpecialized = specializedPower.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalNatural = naturalResourcePower.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalEffective = getTotalEffectivePower();

        return String.format("总有效生产力: %.1f (专精: %.1f, 自然资源: %.1f, 通用: %.1f)",
                totalEffective, totalSpecialized, totalNatural, generalPower);
    }
}