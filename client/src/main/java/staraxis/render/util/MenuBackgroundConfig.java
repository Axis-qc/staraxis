package staraxis.render.util;

import staraxis.game.space.galaxy.GalaxyType;

/**
 * MenuBackgroundConfig（主菜单背景配置）。
 *
 * 所有主菜单 3D 银河背景的可调参数集中在此处，
 * 方便快速调整视觉效果，无需深入渲染代码。
 *
 * 修改后重启客户端即可生效。
 */
public final class MenuBackgroundConfig {

    /** 银河系恒星数量（越多越密集，影响生成耗时和 GPU 负担）。 */
    public static final int STAR_COUNT = 8000;

    /** 世界种子（固定种子确保每次启动显示相同银河）。 */
    public static final long WORLD_SEED = 42L;

    /** 银河系类型（SPIRAL / ELLIPTICAL / IRREGULAR）。 */
    public static final GalaxyType GALAXY_TYPE = GalaxyType.SPIRAL;

    // ── 螺旋星系专用参数（仅 GALAXY_TYPE=SPIRAL 生效） ──

    /** 螺旋臂数量（典型值 2-6）。 */
    public static final int SPIRAL_ARMS = 4;

    /** 螺旋臂倾角（弧度），典型值 0.209 ≈ 12度。 */
    public static final double PITCH_ANGLE = 0.209;

    /** 臂宽度系数（越大臂越宽）。 */
    public static final double ARM_WIDTH = 0.5;

    /** 中心隆起比例（占星系半径的百分比）。 */
    public static final double BULGE_RATIO = 0.2;

    // ── 镜头参数 ──

    /** 镜头自转速度（度/秒），设为 0 可关闭旋转。 */
    public static final float CAMERA_ROTATION_SPEED = 1f;

    /** 镜头初始俯角（度），0=平视，90=俯视。 */
    public static final float CAMERA_PITCH = 30f;

    /** 镜头初始偏航角（度），0=正面。 */
    public static final float CAMERA_YAW = 0f;

    /** 镜头缩放等级（1.0~7.0），值越小距离越远。 */
    public static final double CAMERA_ZOOM = 3.5;

    /** 镜头最大轨道距离（GU），限制最远视野。 */
    public static final double CAMERA_MAX_ORBIT_DIST = 200000;

    /** 镜头近裁剪面。 */
    public static final float CAMERA_NEAR = 10f;

    /** 镜头远裁剪面。 */
    public static final float CAMERA_FAR = 1e6f;

    /** target 坐标边界（GU）。 */
    public static final float CAMERA_TARGET_LIMIT = 100000f;

    private MenuBackgroundConfig() {
    }
}
