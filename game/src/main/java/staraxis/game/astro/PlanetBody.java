package staraxis.game.astro;

import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;
import staraxis.game.planet.PlanetSurface;

/**
 * PlanetBody（行星实体）
 *
 * 继承自 Entity，代表一个行星天体。
 */
public class PlanetBody extends Entity {

    /** 行星类型ID（planetTypeId），例如 "TERRESTRIAL"。 */
    public String planetTypeId;

    /** 行星半径（GU）。 */
    public double radiusGU;

    /**
     * 星球纹理资源路径（相对于 assets/planet/，例如
     * "planet/Solid/Terrestrial/Terrestrial_01-512x512.png"），无时为 null。
     */
    public String surfaceTexturePath;

    // --- Orbit params (merged) ---

    /** 轨道中心实体ID（orbitCenterEntityId）。 */
    public long orbitCenterEntityId;

    /** 轨道长半轴（GU）。 */
    public double semiMajorAxisGU;

    /** 轨道偏心率（0=圆，<1=椭圆）。 */
    public double eccentricity;

    /** 轨道倾角（度）。 */
    public double inclinationDeg;

    /** 升交点经度（度），继承自所属恒星的黄道面角度。 */
    public double longitudeOfAscendingNodeDeg;

    /** 近地点方向角（度）。 */
    public double periapsisArgDeg;

    /** 轨道周期（游戏日）。 */
    public double orbitalPeriodDays;

    /** 纪元时刻（t=0）的平近点角（度）。 */
    public double meanAnomalyDegAtEpoch;

    /** 自转周期（游戏小时）。 */
    public double rotationPeriodHours;

    /** 地表组件ID（surfaceComponentId），0表示未初始化喵。 */
    public long surfaceComponentId;

    /** 地表组件运行时引用（transient，不参与序列化）喵。 */
    public transient PlanetSurface surface;

    public PlanetBody() {
        this.entityType = EntityType.PLANET;
        this.surfaceComponentId = 0;
        this.surface = null;
    }

    /**
     * 检查行星是否有地表组件喵。
     *
     * @return 如果有地表组件（surfaceComponentId != 0），返回true喵。
     */
    public boolean hasSurfaceComponent() {
        return surfaceComponentId != 0;
    }

    /**
     * 检查行星是否有地表区域（需要已初始化地表组件）喵。
     *
     * @return 如果有地表组件且地表区域不为空，返回true喵。
     */
    public boolean hasSurfaceRegions() {
        return surface != null && surface.surfaceRegions != null && !surface.surfaceRegions.isEmpty();
    }

    /**
     * 检查行星是否有城市喵。
     *
     * @return 如果有地表组件且城市列表不为空，返回true喵。
     */
    public boolean hasCities() {
        return surface != null && surface.cities != null && !surface.cities.isEmpty();
    }

    /**
     * 获取行星描述信息的本地化 Key 及其参数喵。
     * 遵循项目“模拟层权威”原则，仅提供数据口径喵。
     *
     * @return 格式化后的描述信息，目前仍返回字符串供兼容使用，但内部已使用 Key 喵。
     */
    public String getDescriptionWithSurface() {
        // 基础信息：astro.planet.desc.base=行星[{0}] 类型:{1} 半径:{2}GU
        String desc = String.format("astro.planet.desc.base|%d|%s|%.2f", entityId, planetTypeId, radiusGU);

        if (hasSurfaceComponent()) {
            if (hasCities()) {
                // astro.planet.desc.hasCities= (有{0}个城市)
                desc += String.format("|astro.planet.desc.hasCities|%d", surface.cities.size());
            } else if (hasSurfaceRegions()) {
                // astro.planet.desc.hasRegions= (有{0}个区域)
                desc += String.format("|astro.planet.desc.hasRegions|%d", surface.surfaceRegions.size());
            } else {
                // astro.planet.desc.noRegions= (有地表组件但未初始化区域)
                desc += "|astro.planet.desc.noRegions";
            }
        } else {
            // astro.planet.desc.noSurface= (无地表组件)
            desc += "|astro.planet.desc.noSurface";
        }
        return desc;
    }
}
