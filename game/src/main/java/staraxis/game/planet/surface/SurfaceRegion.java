package staraxis.game.planet.surface;

import java.util.List;

/**
 * SurfaceRegion（地表区域）
 *
 * 代表大陆或海洋区域，包含次级地貌和可开发空间喵。
 * 每个行星由多个随机命名的地表区域组成，它们共同构成行星100%的地表空间喵。
 */
public class SurfaceRegion {

    /** 区域ID（主键）喵。 */
    public long regionId;

    /** 所属行星实体ID喵。 */
    public long planetEntityId;

    /** 区域类型：CONTINENT（大陆）或 OCEAN（海洋）喵。 */
    public String regionType;

    /** 随机生成名称，例如"希望大陆"喵。 */
    public String name;

    /** 区域占比（0-1），该区域占行星地表总面积的百分比喵。 */
    public double surfacePercentage;

    /** 可开发空间比例（0-1），当前科技水平允许开发的最大可用空间比例喵。 */
    public double developableSpaceRatio;

    /** 次级地貌列表，影响资源发现倾向喵。 */
    public List<SubSurfaceFeature> subFeatures;

    /** 本区域城市ID列表喵。 */
    public List<Long> cityIds;

    /**
     * 默认构造函数喵。
     */
    public SurfaceRegion() {
        // 默认值
        this.developableSpaceRatio = 0.0;
    }

    /**
     * 计算本区域已开发的城市占用空间比例喵。
     * 基于城市规模和行星大小加成计算喵。
     *
     * @return 已开发空间占本区域总面积的比例喵。
     */
    public double calculateDevelopedSpaceRatio() {
        // TODO: 实现基于城市规模和行星大小加成的计算喵
        // 根据文档：totalDevelopmentScale = Σ(city_i.scale * planetSizeModifier)
        // 然后转换为占用本区域的比例喵
        return 0.0;
    }

    /**
     * 检查是否有剩余可开发空间喵。
     *
     * @return 如果已开发空间小于可开发空间，返回true喵。
     */
    public boolean hasRemainingDevelopableSpace() {
        double developed = calculateDevelopedSpaceRatio();
        return developed < developableSpaceRatio * surfacePercentage;
    }

    /**
     * 获取区域类型显示名称喵。
     *
     * @return 区域类型的友好显示名称喵。
     */
    public String getDisplayRegionType() {
        if ("CONTINENT".equals(regionType)) {
            return "大陆";
        } else if ("OCEAN".equals(regionType)) {
            return "海洋";
        }
        return regionType;
    }
}