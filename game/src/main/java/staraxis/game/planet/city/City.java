package staraxis.game.planet.city;

import java.util.ArrayList;
import java.util.List;

/**
 * City（城市）
 *
 * 城市实体，遵循文档中的宏观城市建设理念喵。
 * 行星发展围绕"城市"这一宏观单位，玩家仅指定城市主要专精方向，生产力自动分配喵。
 */
public class City {

    /** 城市ID（主键）喵。 */
    public long cityId;

    /** 所属行星实体ID喵。 */
    public long planetEntityId;

    /** 所属地表区域ID喵。 */
    public long regionId;

    /**
     * 建立本城市的殖民舰实体ID喵。
     * 殖民舰转化为城市实体后保留溯源，0 表示非殖民转化（如开局首都）喵。
     */
    public long sourceShipEntityId;

    /** 城市名称喵。 */
    public String name;

    /** 城市阶段：OUTPOST（前哨殖民地）、SETTLEMENT（定居点）、TOWN（城镇）、CITY（城市）、MEGALOPOLIS（巨型都市）喵。 */
    public String cityStage;

    /** 城市规模（整数档位），表示城市宏观规模喵。 */
    public int cityScale;

    /** 当前人口喵。 */
    public long population;

    /** 人口容量，城市可容纳人口上限喵。公式：cityScale * 10_000喵。 */
    public long populationCap;

    /** 住房数量，根据城市阶段决定喵。 */
    public long housing;

    /** 城市专精ID，决定城市生产力分配的主要类别喵。 */
    public String specializationId;

    /** 是否为行星首都，行星上建立的第一个城市自动成为首都喵。 */
    public boolean isPlanetaryCapital;

    /** 劳动力比例（0-1），可工作人口占总人口比例，默认0.65喵。 */
    public double laborParticipationRate = 0.65;

    /** 已开发资源点ID列表喵。 */
    public List<Long> developedResourceSiteIds;

    /** 分配的城市订单ID列表喵。 */
    public List<Long> assignedOrderIds;

    /** 城市吸引力，影响人口增长和迁移喵。 */
    public double attractiveness;

    /** 上次人口增长结算日喵。 */
    public long lastPopulationGrowthDay;

    /**
     * 默认构造函数喵。
     */
    public City() {
        this.developedResourceSiteIds = new ArrayList<>();
        this.assignedOrderIds = new ArrayList<>();
        this.cityStage = "OUTPOST";
        this.cityScale = 1;
        this.populationCap = calculatePopulationCap();
    }

    /**
     * 计算人口容量喵。
     * 根据文档公式：populationCap = cityScale * 10_000喵。
     *
     * @return 人口容量喵。
     */
    public long calculatePopulationCap() {
        return (long) cityScale * 10_000L;
    }

    /**
     * 计算劳动力数量喵。
     * 公式：floor(population * laborParticipationRate)喵。
     *
     * @return 劳动力数量喵。
     */
    public long calculateLaborForce() {
        return (long) Math.floor(population * laborParticipationRate);
    }

    /**
     * 更新城市阶段，重新计算相关属性喵。
     *
     * @param newStage 新的城市阶段喵。
     */
    public void updateCityStage(String newStage) {
        this.cityStage = newStage;
        // 根据阶段更新规模（可配置，这里使用简单映射）
        this.cityScale = getScaleForStage(newStage);
        this.populationCap = calculatePopulationCap();
        this.housing = calculateHousingForStage(newStage);
    }

    /**
     * 根据阶段获取城市规模喵。
     *
     * @param stage 城市阶段喵。
     * @return 对应的城市规模喵。
     */
    private int getScaleForStage(String stage) {
        switch (stage) {
            case "OUTPOST":
                return 1;
            case "SETTLEMENT":
                return 2;
            case "TOWN":
                return 3;
            case "CITY":
                return 4;
            case "MEGALOPOLIS":
                return 5;
            default:
                return 1;
        }
    }

    /**
     * 根据阶段计算住房数量喵。
     * 城市阶段越高，提供的住房越多喵。
     *
     * @param stage 城市阶段喵。
     * @return 住房数量喵。
     */
    private long calculateHousingForStage(String stage) {
        // 简化公式：住房 = 人口容量 * 住房系数
        double housingFactor;
        switch (stage) {
            case "OUTPOST":
                housingFactor = 0.8;
                break;
            case "SETTLEMENT":
                housingFactor = 0.9;
                break;
            case "TOWN":
                housingFactor = 1.0;
                break;
            case "CITY":
                housingFactor = 1.1;
                break;
            case "MEGALOPOLIS":
                housingFactor = 1.2;
                break;
            default:
                housingFactor = 1.0;
        }
        return (long) (populationCap * housingFactor);
    }

    /**
     * 检查城市是否已达到人口容量喵。
     *
     * @return 如果人口达到或超过容量，返回true喵。
     */
    public boolean isAtPopulationCap() {
        return population >= populationCap;
    }

    /**
     * 获取城市阶段显示名称喵。
     *
     * @return 城市阶段的友好显示名称喵。
     */
    public String getDisplayStage() {
        switch (cityStage) {
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
                return cityStage;
        }
    }

    /**
     * 获取城市描述信息喵。
     *
     * @return 包含名称、阶段、规模的描述字符串喵。
     */
    public String getDescription() {
        return String.format("%s (%s, 规模%d)", name, getDisplayStage(), cityScale);
    }

    /**
     * 添加已开发资源点喵。
     *
     * @param resourceSiteId 资源点ID喵。
     */
    public void addDevelopedResourceSite(long resourceSiteId) {
        if (!developedResourceSiteIds.contains(resourceSiteId)) {
            developedResourceSiteIds.add(resourceSiteId);
        }
    }

    /**
     * 添加分配订单喵。
     *
     * @param orderId 订单ID喵。
     */
    public void addAssignedOrder(long orderId) {
        if (!assignedOrderIds.contains(orderId)) {
            assignedOrderIds.add(orderId);
        }
    }

    /**
     * 移除分配订单喵。
     *
     * @param orderId 订单ID喵。
     */
    public void removeAssignedOrder(long orderId) {
        assignedOrderIds.remove(orderId);
    }

    /**
     * 检查城市是否为蜂巢文明形态（特殊UI名称）喵。
     * 根据文档：蜂巢文明城市建议称为"孵化巢群/巢群节点"等喵。
     *
     * @return 如果是蜂巢文明城市，返回true喵。
     */
    public boolean isHiveCity() {
        // TODO: 根据所属行星/国家的文明形态判断喵
        return false;
    }

    /**
     * 检查城市是否为机械文明形态（特殊UI名称）喵。
     * 根据文档：机械智能城市建议称为"算力节点/机械聚落/工业节点"等喵。
     *
     * @return 如果是机械文明城市，返回true喵。
     */
    public boolean isMachineCity() {
        // TODO: 根据所属行星/国家的文明形态判断喵
        return false;
    }
}