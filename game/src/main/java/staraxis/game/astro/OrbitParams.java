package staraxis.game.astro;

/**
 * OrbitParams
 *
 * 轨道参数（Orbit Params）：描述一个天体绕某个“轨道中心”的运动参数。
 *
 * 说明：
 * - 距离单位：GU。
 * - 角度单位：度。
 * - 轨道中心通过 orbitCenterEntityId 指定，可支持：
 * - 行星绕某颗恒星（orbitCenterEntityId = star.entityId）
 * - 行星绕星系重心实体（orbitCenterEntityId = barycenter.entityId）
 */
public class OrbitParams {

    /** 轨道中心实体ID（orbitCenterEntityId）。 */
    public long orbitCenterEntityId;

    /** 轨道长半轴（GU）。 */
    public double semiMajorAxisGU;

    /** 轨道偏心率（0=圆，<1=椭圆）。 */
    public double eccentricity;

    /** 轨道倾角（度）。 */
    public double inclinationDeg;

    /**
     * 近地点方向角（度）。
     * 在轨道平面内，椭圆长轴相对于 +X 方向的旋转角。
     * 0 表示近地点朝向 +X；90 表示近地点朝向 +Y。
     */
    public double periapsisArgDeg;

    /** 轨道周期（游戏日）。 */
    public double orbitalPeriodDays;

    /**
     * 纪元时刻（t=0）的平近点角（度）。
     * 用于计算任意时刻的轨道位置。
     */
    public double meanAnomalyDegAtEpoch;
}
