package staraxis.render.galaxy;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.collision.Ray;

import staraxis.game.state.DailySettlementState;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.render.WorldCamera;

/**
 * GalaxyViewRenderer（星系视图渲染器）。
 *
 * 从两份快照读取数据：
 * - DailySettlementState：恒星/行星等静态天体基线（每 20 tick 更新）
 * - RealTimeWorldState：动态实体（SHIP 等，每 tick 更新）
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

    public void render(RealTimeWorldState highFreq, DailySettlementState lowFreq, WorldCamera camera,
            long hoveredStarId) {
        // 首次渲染时从低频基线提取恒星快照重建实例缓冲区（恒星数据在游戏生命周期内不变）
        if (!batchRenderer.isBuilt()) {
            List<EntitySnapshot> stars = extractStarsFromBaselines(lowFreq);
            batchRenderer.rebuild(stars);
            haloRenderer.rebuild(stars);
        }

        batchRenderer.render(camera, hoveredStarId);
        haloRenderer.render(camera);

        if (hoveredStarId >= 0) {
            float[] pos = batchRenderer.getStarPosition(hoveredStarId);
            if (pos != null) {
                drawSelectionRing(pos[0], pos[1], pos[2], camera);
            }
        }
    }

    /**
     * 从低频基线中提取所有 STAR 类型实体快照。
     */
    private static List<EntitySnapshot> extractStarsFromBaselines(DailySettlementState lowFreq) {
        List<EntitySnapshot> stars = new ArrayList<>();
        if (lowFreq == null || lowFreq.publicEntityBaselinesBySectorKey == null)
            return stars;
        for (List<EntitySnapshot> baselines : lowFreq.publicEntityBaselinesBySectorKey.values()) {
            for (EntitySnapshot s : baselines) {
                if (s != null && s.entityType == staraxis.game.entity.EntityType.STAR) {
                    stars.add(s);
                }
            }
        }
        return stars;
    }

    public long pick(Ray ray, @SuppressWarnings("unused") RealTimeWorldState state) {
        return batchRenderer.pick(ray);
    }

    /** 根据恒星 entityId 获取世界坐标，供 tooltip 投影等使用。 */
    public float[] getStarPosition(long starId) {
        return batchRenderer.getStarPosition(starId);
    }

    /**
     * 在恒星位置绘制一个面向镜头的圆环（billboard ring）。
     */
    private void drawSelectionRing(float x, float y, float z, WorldCamera worldCamera) {
        drawBillboardRing(x, y, z, worldCamera, 200f, 0.3f, 0.8f, 1f, 0.8f);
    }

    /**
     * 绘制面向镜头的 billboard 圆环。
     *
     * @param x,     y, z 世界坐标
     * @param camera 当前相机
     * @param radius 圆环半径（GU）
     * @param r,     g, b, a 颜色分量
     */
    private void drawBillboardRing(float x, float y, float z, WorldCamera worldCamera,
            float radius, float r, float g, float b, float a) {
        int segments = 24;

        var cam = worldCamera.camera;
        // right = direction × up（已由 WorldCamera.update 维护）
        float rx = cam.direction.y * cam.up.z - cam.direction.z * cam.up.y;
        float ry = cam.direction.z * cam.up.x - cam.direction.x * cam.up.z;
        float rz = cam.direction.x * cam.up.y - cam.direction.y * cam.up.x;
        float rLen = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rLen > 1e-6f) {
            rx /= rLen;
            ry /= rLen;
            rz /= rLen;
        }
        // up 直接用相机 up
        float ux = cam.up.x, uy = cam.up.y, uz = cam.up.z;

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        shapeRenderer.setProjectionMatrix(cam.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(r, g, b, a);
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

    /**
     * 重置渲染器内部状态，强制下次 render 时从新快照重新构建实例缓冲区喵。
     *
     * 使用场景：退出游戏返回主菜单后，重新开始新游戏时调用，
     * 确保新世界的恒星数据替换旧世界的 GPU 缓冲区喵。
     */
    public void reset() {
        batchRenderer.reset();
        haloRenderer.reset();
    }
}
