package staraxis.render.galaxy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.collision.Ray;

import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.render.WorldCamera;

/**
 * GalaxyViewRenderer（星系视图渲染器）。
 *
 * 从 RealTimeWorldState 中读取恒星快照并渲染。
 * 使用 StarBatchRenderer 批量渲染（球形模型）。
 */
public class GalaxyViewRenderer {

    private final ShapeRenderer shapeRenderer;
    private final StarBatchRenderer batchRenderer;

    public GalaxyViewRenderer() {
        this.shapeRenderer = new ShapeRenderer();
        this.batchRenderer = new StarBatchRenderer();
    }

    public void render(RealTimeWorldState state, WorldCamera camera, long hoveredStarId) {
        batchRenderer.render(state, camera, hoveredStarId);

        if (hoveredStarId >= 0) {
            // 渲染时顺便查一次位置传递给 drawSelectionBox，避免二次遍历
            EntitySnapshot star = findStar(state, hoveredStarId);
            if (star != null) {
                drawSelectionBox(star, camera.camera.combined);
            }
        }
    }

    public long pick(Ray ray, RealTimeWorldState state) {
        return batchRenderer.pick(ray, state);
    }

    private void drawSelectionBox(EntitySnapshot star, Matrix4 projectionView) {
        float x = (float) star.posWorldGU.x();
        float y = (float) star.posWorldGU.y();
        float z = (float) star.posWorldGU.z();
        float s = 200f;

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        shapeRenderer.setProjectionMatrix(projectionView);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.3f, 0.8f, 1f, 0.8f);
        Gdx.gl.glLineWidth(2f);

        shapeRenderer.line(x - s, y, z - s, x + s, y, z - s);
        shapeRenderer.line(x + s, y, z - s, x + s, y, z + s);
        shapeRenderer.line(x + s, y, z + s, x - s, y, z + s);
        shapeRenderer.line(x - s, y, z + s, x - s, y, z - s);

        shapeRenderer.end();
    }

    private EntitySnapshot findStar(RealTimeWorldState state, long starId) {
        for (EntitySnapshot snap : state.getEntitySnapshotsView()) {
            if (snap != null && snap.entityId == starId) {
                return snap;
            }
        }
        return null;
    }

    public void dispose() {
        shapeRenderer.dispose();
        batchRenderer.dispose();
    }
}
