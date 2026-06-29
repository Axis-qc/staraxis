package staraxis.render.picking;

import com.badlogic.gdx.math.collision.Ray;

import staraxis.game.space.galaxy.GalaxyData;
import staraxis.render.WorldCamera;
import staraxis.render.galaxy.GalaxyViewRenderer;

/**
 * RayPicker（射线拾取器）。
 *
 * 在 Galaxy View 中，从相机发射射线，用 Intersector.intersectRayBounds
 * 直接检测恒星球体模型。
 */
public class RayPicker {

    /** 缓存的悬停恒星ID。 */
    private long hoveredStarId = -1;

    /**
     * 更新悬停恒星。
     *
     * @param camera 世界相机
     * @param galaxy 星系数据
     * @param screenX 鼠标屏幕 X
     * @param screenY 鼠标屏幕 Y
     * @param galaxyView 星系视图渲染器
     */
    public void updateHovered(WorldCamera camera, GalaxyData galaxy, int screenX, int screenY, GalaxyViewRenderer galaxyView) {
        Ray ray = camera.camera.getPickRay(screenX, screenY);
        hoveredStarId = galaxyView.pick(ray, galaxy);
    }

    /**
     * 获取当前悬停的恒星ID。
     */
    public long getHoveredStarId() {
        return hoveredStarId;
    }
}
