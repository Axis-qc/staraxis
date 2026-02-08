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
     * 获取行星描述信息，包含地表状态喵。
     *
     * @return 行星描述字符串喵。
     */
    public String getDescriptionWithSurface() {
        String baseDesc = String.format("行星[%d] 类型:%s 半径:%.2fGU", entityId, planetTypeId, radiusGU);
        if (hasSurfaceComponent()) {
            if (hasCities()) {
                return baseDesc + String.format(" (有%d个城市)", surface.cities.size());
            } else if (hasSurfaceRegions()) {
                return baseDesc + String.format(" (有%d个区域)", surface.surfaceRegions.size());
            } else {
                return baseDesc + " (有地表组件但未初始化区域)";
            }
        } else {
            return baseDesc + " (无地表组件)";
        }
    }
}
