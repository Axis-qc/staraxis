package staraxis.render.galaxy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.collision.Ray;

import staraxis.game.state.RealTimeWorldState;
import staraxis.render.WorldCamera;

/**
 * GalaxyViewRenderer（星系视图渲染器）。
 *
 * 从 RealTimeWorldState 中读取恒星快照并渲染。
 * 使用 StarBatchRenderer 批量渲染（GPU 实例化，一次 draw call 绘制全部恒星）。
 */
public class GalaxyViewRenderer {

    private final ShapeRenderer shapeRenderer;
    private final StarBatchRenderer batchRenderer;
    private final StarHaloRenderer haloRenderer;

    public GalaxyViewRenderer() {
        this.shapeRenderer = new ShapeRenderer();
        this.batchRenderer = new StarBatchRenderer();
        this.haloRenderer = new StarHaloRenderer();
    }

    public void render(RealTimeWorldState state, WorldCamera camera, long hoveredStarId) {
        // 首次渲染或恒星列表变化时重建实例缓冲区
        if (!batchRenderer.isBuilt()) {
            batchRenderer.rebuild(state);
            haloRenderer.rebuild(state);
        }

        batchRenderer.render(camera, hoveredStarId);

        //  光晕层：在恒星球之后叠加，距离补偿提亮让星系整体明亮
        haloRenderer.render(camera);

        // 悬停恒星的选择框
        if (hoveredStarId >= 0) {
            float[] pos = batchRenderer.getStarPosition(hoveredStarId);
            if (pos != null) {
                drawSelectionBox(pos[0], pos[1], pos[2], camera.camera.combined);
            }
        }
    }

    public long pick(Ray ray, @SuppressWarnings("unused") RealTimeWorldState state) {
        return batchRenderer.pick(ray);
    }

    private void drawSelectionBox(float x, float y, float z, Matrix4 projectionView) {
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

    public void dispose() {
        shapeRenderer.dispose();
        batchRenderer.dispose();
        haloRenderer.dispose();
    }
}
