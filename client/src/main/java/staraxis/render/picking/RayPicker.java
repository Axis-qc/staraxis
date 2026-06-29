package staraxis.render.picking;

import com.badlogic.gdx.math.collision.Ray;

import staraxis.game.state.RealTimeWorldState;
import staraxis.render.WorldCamera;
import staraxis.render.galaxy.GalaxyViewRenderer;

/**
 * RayPicker（射线拾取器）。
 *
 * 在 Galaxy View 中，从相机发射射线，用 Intersector.intersectRayBounds
 * 直接检测恒星球体模型。
 */
public class RayPicker {

    private long hoveredStarId = -1;

    public void updateHovered(WorldCamera camera, RealTimeWorldState state, int screenX, int screenY, GalaxyViewRenderer galaxyView) {
        Ray ray = camera.camera.getPickRay(screenX, screenY);
        hoveredStarId = galaxyView.pick(ray, state);
    }

    public long getHoveredStarId() {
        return hoveredStarId;
    }
}
