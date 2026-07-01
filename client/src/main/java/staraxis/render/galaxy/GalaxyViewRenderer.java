package staraxis.render.galaxy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
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

        // 悬停恒星的选择环（面向镜头的圆环）
        if (hoveredStarId >= 0) {
            float[] pos = batchRenderer.getStarPosition(hoveredStarId);
            if (pos != null) {
                drawSelectionRing(pos[0], pos[1], pos[2], camera);
            }
        }
    }

    public long pick(Ray ray, @SuppressWarnings("unused") RealTimeWorldState state) {
        return batchRenderer.pick(ray);
    }

    /**
     * 在恒星位置绘制一个面向镜头的圆环（billboard ring）。
     * 用相机 right/up 向量构建圆环 24 个顶点，连线绘制。
     */
    private void drawSelectionRing(float x, float y, float z, WorldCamera worldCamera) {
        float radius = 200f;
        int segments = 24;

        var cam = worldCamera.camera;
        // right = direction × up（已由 WorldCamera.update 维护）
        float rx = cam.direction.y * cam.up.z - cam.direction.z * cam.up.y;
        float ry = cam.direction.z * cam.up.x - cam.direction.x * cam.up.z;
        float rz = cam.direction.x * cam.up.y - cam.direction.y * cam.up.x;
        float rLen = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rLen > 1e-6f) { rx /= rLen; ry /= rLen; rz /= rLen; }
        // up 直接用相机 up
        float ux = cam.up.x, uy = cam.up.y, uz = cam.up.z;

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        shapeRenderer.setProjectionMatrix(cam.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.3f, 0.8f, 1f, 0.8f);
        Gdx.gl.glLineWidth(2f);

        float step = (float) (Math.PI * 2 / segments);
        for (int i = 0; i < segments; i++) {
            float a1 = i * step;
            float a2 = (i + 1) * step;
            float cos1 = (float) Math.cos(a1), sin1 = (float) Math.sin(a1);
            float cos2 = (float) Math.cos(a2), sin2 = (float) Math.sin(a2);
            float p1x = x + radius * (rx * cos1 + ux * sin1);
            float p1y = y + radius * (ry * cos1 + uy * sin1);
            float p1z = z + radius * (rz * cos1 + uz * sin1);
            float p2x = x + radius * (rx * cos2 + ux * sin2);
            float p2y = y + radius * (ry * cos2 + uy * sin2);
            float p2z = z + radius * (rz * cos2 + uz * sin2);
            shapeRenderer.line(p1x, p1y, p1z, p2x, p2y, p2z);
        }

        shapeRenderer.end();
    }

    public void dispose() {
        shapeRenderer.dispose();
        batchRenderer.dispose();
        haloRenderer.dispose();
    }
}
