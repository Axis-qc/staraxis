package staraxis.render.picking;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

import staraxis.game.space.galaxy.GalaxyData;
import staraxis.game.space.galaxy.StarPosition;
import staraxis.render.WorldCamera;

/**
 * RayPicker（射线拾取器）。
 *
 * 在 Galaxy View 中，从相机发射射线，计算到每个恒星球体中心的最近距离。
 * 选中距离最小的恒星（在阈值内）。
 */
public class RayPicker {

    /** 临时向量复用（避免GC）。 */
    private final Vector3 tmpV1 = new Vector3();
    private final Vector3 tmpV2 = new Vector3();

    /** 拾取半径（GU）。 */
    private double pickRadius = 600.0;

    /** 缓存的悬停恒星ID。 */
    private long hoveredStarId = -1;

    /**
     * 更新悬停恒星。
     *
     * @param camera 世界相机
     * @param galaxy 星系数据
     * @param screenX 鼠标屏幕 X
     * @param screenY 鼠标屏幕 Y
     */
    public void updateHovered(WorldCamera camera, GalaxyData galaxy, int screenX, int screenY) {
        Ray ray = camera.camera.getPickRay(screenX, screenY);

        hoveredStarId = -1;
        double bestDist = pickRadius;

        for (StarPosition star : galaxy.stars) {
            // 计算射线到恒星球体中心的最短距离
            // C = O + D * t, t = (P - O)·D
            tmpV1.set(ray.origin);
            tmpV2.set((float) star.galaxyX(), (float) star.galaxyY(), (float) star.galaxyZ());

            float dot = tmpV2.sub(tmpV1).dot(ray.direction);

            // 射线起点的垂足位置
            tmpV1.set(ray.direction).scl(dot).add(ray.origin);

            // 垂足到恒星中心的距离
            double dist = tmpV2.set((float) star.galaxyX(), (float) star.galaxyY(), (float) star.galaxyZ())
                .sub(tmpV1).len();

            if (dist < bestDist) {
                bestDist = dist;
                hoveredStarId = star.starId();
            }
        }
    }

    /**
     * 获取当前悬停的恒星ID。
     */
    public long getHoveredStarId() {
        return hoveredStarId;
    }

    /**
     * 设置拾取半径。
     */
    public void setPickRadius(double radius) {
        this.pickRadius = radius;
    }
}
