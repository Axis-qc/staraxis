package staraxis.game.planet.resource;

/**
 * ResourceSite（资源点）
 *
 * 自然资源点，提供生产力或吸引力加成喵。
 * 城市会自动开发其范围内的资源点，开发后的资源点直接提供自然资源生产力喵。
 */
public class ResourceSite {

    /** 资源点ID（主键）喵。 */
    public long resourceSiteId;

    /** 所属行星实体ID喵。 */
    public long planetEntityId;

    /** 所属地表区域ID喵。 */
    public long regionId;

    /** 开发城市ID，0表示未开发喵。 */
    public long developedByCityId;

    /** 资源类型：MINERAL（矿产）、AGRICULTURE（农业）、FISHERY（渔业）、FOREST（林业）、LANDSCAPE（景观）等喵。 */
    public String resourceType;

    /** 子类型，如"富饶铁矿脉"、"肥沃农田"等喵。 */
    public String subType;

    /** 提供的生产力类别，如"MINING"（采矿）、"FARMING"（农业）、"INDUSTRY"（工业）等喵。 */
    public String productionCategory;

    /** 提供的生产力值，资源点被开发后直接为该城市提供的生产力喵。 */
    public double productionPower;

    /** 是否为景观，景观不提供生产力，但提供吸引力加成喵。 */
    public boolean isLandmark;

    /** 吸引力加成，景观建筑提供的固定加成喵。 */
    public double attractivenessBonus;

    /** 资源点规模，影响生产力值和发现难度喵。 */
    public String sizeTier; // SMALL, MEDIUM, LARGE, RICH

    /** 资源点状态：UNDISCOVERED（未发现）、DISCOVERED（已发现但未开发）、DEVELOPED（已开发）、DEPLETED（枯竭）喵。 */
    public String status;

    /** 发现日期（游戏日）喵。 */
    public long discoveredDay;

    /** 开发日期（游戏日）喵。 */
    public long developedDay;

    /**
     * 默认构造函数喵。
     */
    public ResourceSite() {
        this.status = "UNDISCOVERED";
        this.developedByCityId = 0;
        this.isLandmark = false;
        this.attractivenessBonus = 0.0;
    }

    /**
     * 标记为已发现喵。
     *
     * @param discoveryDay 发现日期喵。
     */
    public void markAsDiscovered(long discoveryDay) {
        this.status = "DISCOVERED";
        this.discoveredDay = discoveryDay;
    }

    /**
     * 开发资源点喵。
     *
     * @param cityId 开发城市ID喵。
     * @param developmentDay 开发日期喵。
     * @return 开发是否成功喵。
     */
    public boolean develop(long cityId, long developmentDay) {
        if (status.equals("DISCOVERED") && developedByCityId == 0) {
            this.status = "DEVELOPED";
            this.developedByCityId = cityId;
            this.developedDay = developmentDay;
            return true;
        }
        return false;
    }

    /**
     * 检查资源点是否已开发喵。
     *
     * @return 如果状态为DEVELOPED，返回true喵。
     */
    public boolean isDeveloped() {
        return "DEVELOPED".equals(status);
    }

    /**
     * 检查资源点是否可开发喵。
     *
     * @return 如果状态为DISCOVERED且未分配给任何城市，返回true喵。
     */
    public boolean isDevelopable() {
        return "DISCOVERED".equals(status) && developedByCityId == 0;
    }

    /**
     * 获取资源类型显示名称喵。
     *
     * @return 资源类型的友好显示名称喵。
     */
    public String getDisplayResourceType() {
        switch (resourceType) {
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
                return resourceType;
        }
    }

    /**
     * 获取生产力类别显示名称喵。
     *
     * @return 生产力类别的友好显示名称喵。
     */
    public String getDisplayProductionCategory() {
        switch (productionCategory) {
            case "MINING":
                return "采矿生产力";
            case "FARMING":
                return "农业生产力";
            case "INDUSTRY":
                return "工业生产力";
            case "RESEARCH":
                return "科研生产力";
            case "ADMINISTRATION":
                return "行政生产力";
            case "MILITARY":
                return "军工生产力";
            default:
                return productionCategory;
        }
    }

    /**
     * 获取资源点描述信息喵。
     *
     * @return 包含资源类型、子类型和状态的描述字符串喵。
     */
    public String getDescription() {
        if (isLandmark) {
            return String.format("%s【景观】- %s", getDisplayResourceType(), subType);
        } else {
            return String.format("%s - %s (%s)", getDisplayResourceType(), subType, getSizeTierDisplay());
        }
    }

    /**
     * 获取规模等级显示名称喵。
     *
     * @return 规模等级的友好显示名称喵。
     */
    public String getSizeTierDisplay() {
        if (sizeTier == null) return "普通";
        switch (sizeTier) {
            case "SMALL":
                return "小型";
            case "MEDIUM":
                return "中型";
            case "LARGE":
                return "大型";
            case "RICH":
                return "富饶";
            default:
                return sizeTier;
        }
    }

    /**
     * 获取实际提供的生产力值（考虑开发状态和景观）喵。
     *
     * @return 如果已开发且不是景观，返回productionPower；否则返回0喵。
     */
    public double getEffectiveProductionPower() {
        if (isDeveloped() && !isLandmark) {
            return productionPower;
        }
        return 0.0;
    }

    /**
     * 获取实际提供的吸引力加成喵。
     *
     * @return 如果是景观且已开发，返回attractivenessBonus；否则返回0喵。
     */
    public double getEffectiveAttractivenessBonus() {
        if (isLandmark && isDeveloped()) {
            return attractivenessBonus;
        }
        return 0.0;
    }

    /**
     * 检查是否为矿产资源点喵。
     *
     * @return 如果resourceType为MINERAL，返回true喵。
     */
    public boolean isMineralResource() {
        return "MINERAL".equals(resourceType);
    }

    /**
     * 检查是否为农业资源点喵。
     *
     * @return 如果resourceType为AGRICULTURE，返回true喵。
     */
    public boolean isAgricultureResource() {
        return "AGRICULTURE".equals(resourceType);
    }

    /**
     * 检查是否为景观资源点喵。
     *
     * @return 如果isLandmark为true，返回true喵。
     */
    public boolean isLandmarkResource() {
        return isLandmark;
    }
}