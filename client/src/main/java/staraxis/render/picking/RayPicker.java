package staraxis.render.picking;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

import staraxis.game.space.galaxy.GalaxyData;
import staraxis.game.space.galaxy.StarPosition;
import staraxis.render.WorldCamera;

/**
 * RayPicker（射线拾取器）。
 *
 * 在 Galaxy View 中，从相机发射射线，找到最近的恒星。
 * 返回选中的 starId（-1 表示未选中）。
 */
public class RayPicker {

    /** 拾取距离阈值（GU），鼠标距离恒星中心小于此值视为选中。 */
    private double pickThreshold = 500.0;

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

        // 与星系盘面（Y=0）求交
        Plane diskPlane = new Plane(new Vector3(0, 1, 0), 0);
        Vector3 hitPoint = new Vector3();
        boolean hit = Intersector.intersectRayPlane(ray, diskPlane, hitPoint);

        hoveredStarId = -1;

        if (hit) {
            double bestDist = pickThreshold;

            for (StarPosition star : galaxy.stars) {
                double dx = hitPoint.x - star.galaxyX();
                double dz = hitPoint.z - star.galaxyZ();
                double dist = Math.sqrt(dx * dx + dz * dz);

                if (dist < bestDist) {
                    bestDist = dist;
                    hoveredStarId = star.starId();
                }
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
     * 设置拾取距离阈值。
     */
    public void setPickThreshold(double threshold) {
        this.pickThreshold = threshold;
    }
}
