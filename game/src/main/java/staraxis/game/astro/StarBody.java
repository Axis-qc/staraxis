package staraxis.game.astro;

import staraxis.game.entity.Entity;
import staraxis.game.entity.EntityType;
import staraxis.game.space.OrbitalElements;
import staraxis.game.space.SpacePosition;

/**
 * StarBody（恒星实体）
 *
 * 继承自 Entity，代表一个恒星天体。
 *
 * 系统坐标体系（层级引力）：
 * - systemPos：恒星在 StarSystem 局部空间中的坐标
 * - orbitCenterEntityId：指向引力中心实体（0=系统重心 barycenter）
 * - orbitalElements：绕引力中心的轨道根数（null=静止）
 *
 * 单星系统：systemPos=(0,0,0)，orbitCenterEntityId=0，orbitalElements=null
 * 多星系统：每颗恒星有各自的 systemPos 和 orbitalElements
 */
public class StarBody extends Entity {

    /** 恒星类型ID（starTypeId），例如 "G_MAIN_SEQUENCE"。 */
    public String starTypeId;

    /** 恒星半径（GU）。 */
    public double radiusGU;

    /** 恒星质量（太阳质量倍数）。 */
    public double massSolar;

    /** 表面温度（开尔文）。 */
    public int temperatureK;

    /** 恒星描述文本。 */
    public String description;

    /**
     * 恒星纹理资源路径（相对于 assets/star/，例如 "star/star_yellow01.png"），无时为 null。
     */
    public String surfaceTexturePath;

    // --- System View 坐标与轨道体系 ---

    /** 恒星在系统局部空间中的位置偏移（System View 坐标）。
     *  单星：SpacePosition.ORIGIN；多星：相对于系统重心的偏移。 */
    public SpacePosition systemPos = SpacePosition.ORIGIN;

    /** 黄道面角度（度），该恒星的盘面绕 Y 轴的旋转角。
     *  该恒星所有行星以此为基准角生成轨道。 */
    public double eclipticAngleDeg = 0.0;

    /** 引力中心实体ID（orbitCenterEntityId）。
     *  0 = 系统重心（barycenterEntityId）；
     *  非0 = 绕指定实体公转（多星层级用）。
     *  渲染时位置 = 引力中心的位置 + OrbitSolver.solve(orbitalElements, t)。 */
    public long orbitCenterEntityId = 0L;

    /** 轨道根数（绕 orbitCenterEntityId 公转的轨道）。
     *  null = 静止（单星系统或重心实体自身）。 */
    public OrbitalElements orbitalElements;

    public StarBody() {
        this.entityType = EntityType.STAR;
    }
}
