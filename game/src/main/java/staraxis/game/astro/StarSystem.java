package staraxis.game.astro;

import staraxis.game.space.SpacePosition;
import staraxis.game.world.hex.SectorCoord;

import java.util.ArrayList;
import java.util.List;

/**
 * StarSystem（恒星系）
 *
 * 恒星系：一个逻辑组织，用于表达"这几颗恒星与行星属于同一系统/同一重心参考系"。
 *
 * 坐标体系：
 * - galaxyPos：星系坐标中的位置（3D，SpacePosition）
 * - 恒星位置（StarBody.systemPos）和行星轨道均在系统局部空间定义
 * - 系统重心 = galaxyPos，用于星系视图和FTL到达
 */
public class StarSystem {
    /** 恒星系ID（systemId）。 */
    public long systemId;

    /** 恒星系重心实体ID（barycenterEntityId），用于行星轨道指向。 */
    public long barycenterEntityId;

    /** 恒星系当前所在星区坐标（sectorCoord = sectorId 口径）。 */
    public SectorCoord sectorCoord;

    /** 恒星系在星系坐标中的 3D 位置（GU）。
     *  替代旧的 centerWorldGU（Vec2d），Z 轴对应原 Vec2d.y()。 */
    public SpacePosition galaxyPos;

    /** 系统重力井半径（GU），定义 FTL 到达边界/禁入区。
     *  由生成器在行星生成完毕后计算。 */
    public double gravityWellRadiusGU;

    /** 属于该系统的恒星实体列表。 */
    public final List<StarBody> stars = new ArrayList<>();

    /** 属于该系统的行星实体列表。 */
    public final List<PlanetBody> planets = new ArrayList<>();

    /** 恒星系所属国家ID（权威归属口径）。 */
    public String ownerNationId;

    /**
     * 权威分配系统归属。
     * 作用：同步设置系统、重心、所有恒星及行星的 ownerNationId（所属国家ID），确保一致性喵！
     *
     * @param nationId 国家唯一标识
     */
    public void assignOwnership(String nationId) {
        this.ownerNationId = nationId;
        if (stars != null) {
            for (StarBody star : stars) {
                if (star != null) {
                    star.ownerNationId = nationId;
                }
            }
        }
        if (planets != null) {
            for (PlanetBody planet : planets) {
                if (planet != null) {
                    planet.ownerNationId = nationId;
                }
            }
        }
    }
}
