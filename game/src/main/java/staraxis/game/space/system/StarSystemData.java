package staraxis.game.space.system;

import java.util.List;

import staraxis.game.space.galaxy.StarPosition;

/**
 * StarSystemData（恒星系数据）。
 *
 * 包含主恒星和围绕它运行的行星列表。
 * 用于 System View 渲染。
 *
 * 恒星在 System View 中位于原点 (0,0,0)。
 * 行星位置由 OrbitalElements + OrbitSolver 实时计算。
 */
public class StarSystemData {

    /** 恒星系ID（= 主恒星ID）。 */
    public final long systemId;

    /** 主恒星数据（包含 galaxyX/Y/Z 星系坐标）。 */
    public final StarPosition mainStar;

    /** 行星列表。 */
    public final List<PlanetData> planets;

    public StarSystemData(long systemId, StarPosition mainStar, List<PlanetData> planets) {
        this.systemId = systemId;
        this.mainStar = mainStar;
        this.planets = List.copyOf(planets);
    }

    /**
     * 行星数量。
     */
    public int planetCount() {
        return planets.size();
    }
}
