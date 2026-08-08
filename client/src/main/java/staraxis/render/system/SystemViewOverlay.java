package staraxis.render.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import staraxis.game.entity.EntityType;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.game.state.snapshot.EntitySnapshot.PlanetDetails;
import staraxis.render.WorldCamera;
import staraxis.render.lod.LodCalculator;
import staraxis.render.lod.LodLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SystemViewOverlay（System View 2D 屏幕圆标叠加层）。
 *
 * 从 SystemViewRenderer 抽取，负责：
 * - 天体/舰船的 2D 屏幕圆标渲染（距离远时替代 3D 模型）
 * - 2D 圆标屏幕空间拾取
 * - 天体颜色映射
 *
 * 纯快照驱动：所有天体数据由 EntitySnapshot 列表提供，不依赖 game 层类型。
 */
public class SystemViewOverlay {

    private final ShapeRenderer shapeRenderer = new ShapeRenderer();

    private final List<PlanetDotInfo> planetDotInfos = new ArrayList<>();

    private static final float DOT_RADIUS_PX = 10f;

    /** 选中边框半边长（px）喵 */
    private static final float SELECT_FRAME_HALF = 14f;
    /** 选中边框角标线段长度（px）喵 */
    private static final float SELECT_FRAME_TAB = 6f;
    /** 选中边框颜色（与舰船选中高亮一致的亮黄）喵 */
    private static final com.badlogic.gdx.graphics.Color SELECT_FRAME_COLOR =
            new com.badlogic.gdx.graphics.Color(1.0f, 0.9f, 0.2f, 1f);

    private final Vector3 tmpScreenPos = new Vector3();

    private static class PlanetDotInfo {
        final long entityId;
        final float screenX;
        final float screenY;

        PlanetDotInfo(long id, float x, float y) {
            entityId = id;
            screenX = x;
            screenY = y;
        }
    }

    /**
     * 渲染天体 UI 圆标（行星、小行星、卫星）。
     *
     * 基于相机 orbitDist 做 alpha 渐变：
     *   orbitDist > 20000 完全不透明
     *   5000 < orbitDist < 20000 线性淡入
     *   orbitDist < 5000 完全透明
     */
    public void renderPlanetDots(List<EntitySnapshot> allSystemSnapshots, WorldCamera camera,
                                  Map<Long, Vector3> bodyCenterIndex) {
        planetDotInfos.clear();

        // 过滤出行星/小行星/卫星快照
        List<EntitySnapshot> bodies = new ArrayList<>();
        for (EntitySnapshot snap : allSystemSnapshots) {
            if (snap != null && snap.details instanceof PlanetDetails) {
                bodies.add(snap);
            }
        }
        if (bodies.isEmpty()) return;

        float dotAlpha = LodCalculator.calculateDotAlpha(camera.getOrbitDistance());
        if (dotAlpha <= 0f) return;

        float gfxW = Gdx.graphics.getWidth();
        float gfxH = Gdx.graphics.getHeight();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(new Matrix4().setToOrtho(0f, gfxW, gfxH, 0f, -1f, 1f));
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        Vector3 camPos = camera.camera.position;

        for (EntitySnapshot snap : bodies) {
            if (!(snap.details instanceof PlanetDetails pd)) continue;
            Vector3 bodyPos = bodyCenterIndex.get(snap.entityId);
            if (bodyPos == null) continue;

            float px = bodyPos.x, py = bodyPos.y, pz = bodyPos.z;

            double dist = Math.sqrt(
                    (camPos.x - px) * (camPos.x - px) +
                            (camPos.y - py) * (camPos.y - py) +
                            (camPos.z - pz) * (camPos.z - pz));
            if (LodCalculator.calculate(dist) == LodLevel.HIDDEN) continue;

            tmpScreenPos.set(px, py, pz);
            camera.camera.project(tmpScreenPos);
            if (tmpScreenPos.z < 0f || tmpScreenPos.z > 1f) continue;

            float dotY = gfxH - tmpScreenPos.y;

            float dotRadius = (snap.entityType == EntityType.ASTEROID || snap.entityType == EntityType.MOON)
                    ? DOT_RADIUS_PX * 0.5f
                    : DOT_RADIUS_PX;

            float[] rgb = planetColor(pd.planetTypeId);
            shapeRenderer.setColor(rgb[0], rgb[1], rgb[2], dotAlpha);
            shapeRenderer.circle(tmpScreenPos.x, dotY, dotRadius);

            planetDotInfos.add(new PlanetDotInfo(snap.entityId, tmpScreenPos.x, dotY));
        }

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * 渲染选中星体边框（2D 屏幕空间战术框：四角 L 形角标）。
     *
     * 选中实体位置从 bodyCenterIndex 取（含恒星/行星/小行星/卫星）。
     * 边框固定在屏幕尺寸（不随距离缩放），屏幕边缘自动钳制保证完整可见喵。
     *
     * @param selectedEntityId 选中实体 ID，&lt; 0 时不渲染
     * @param camera           当前相机
     * @param bodyCenterIndex  天体位置索引
     */
    public void renderSelectedFrame(long selectedEntityId, WorldCamera camera,
                                    Map<Long, Vector3> bodyCenterIndex) {
        if (selectedEntityId < 0) return;
        Vector3 bodyPos = bodyCenterIndex.get(selectedEntityId);
        if (bodyPos == null) return;

        float gfxW = Gdx.graphics.getWidth();
        float gfxH = Gdx.graphics.getHeight();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(new Matrix4().setToOrtho(0f, gfxW, gfxH, 0f, -1f, 1f));

        tmpScreenPos.set(bodyPos);
        camera.camera.project(tmpScreenPos);
        if (tmpScreenPos.z < 0f || tmpScreenPos.z > 1f) {
            Gdx.gl.glDisable(GL20.GL_BLEND);
            return;
        }
        float cx = tmpScreenPos.x;
        float cy = gfxH - tmpScreenPos.y;

        // 屏幕边缘钳制，保证边框完整可见喵
        cx = Math.max(SELECT_FRAME_HALF, Math.min(cx, gfxW - SELECT_FRAME_HALF));
        cy = Math.max(SELECT_FRAME_HALF, Math.min(cy, gfxH - SELECT_FRAME_HALF));

        float h = SELECT_FRAME_HALF;
        float t = SELECT_FRAME_TAB;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(SELECT_FRAME_COLOR);
        // 左下角标
        shapeRenderer.line(cx - h, cy - h, cx - h + t, cy - h);
        shapeRenderer.line(cx - h, cy - h, cx - h, cy - h + t);
        // 右下角标
        shapeRenderer.line(cx + h, cy - h, cx + h - t, cy - h);
        shapeRenderer.line(cx + h, cy - h, cx + h, cy - h + t);
        // 左上角标
        shapeRenderer.line(cx - h, cy + h, cx - h + t, cy + h);
        shapeRenderer.line(cx - h, cy + h, cx - h, cy + h - t);
        // 右上角标
        shapeRenderer.line(cx + h, cy + h, cx + h - t, cy + h);
        shapeRenderer.line(cx + h, cy + h, cx + h, cy + h - t);
        shapeRenderer.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * 2D 圆标拾取。返回命中的最佳实体 ID，无命中返回 -1。
     * 调用方应将其与 3D 拾取结果比较距离后取最近者。
     * 舰船由 StarEaterShipRenderer（3D 模型拾取）处理，此处仅拾取天体圆标。
     */
    public long pickDots(int screenX, int screenY, long currentBestId, float currentBestDistSq,
                          Map<Long, Vector3> bodyCenterIndex, Vector3 cameraPos) {
        long bestId = currentBestId;
        float bestDistSq = currentBestDistSq;

        for (PlanetDotInfo dot : planetDotInfos) {
            float dx = screenX - dot.screenX;
            float dy = screenY - dot.screenY;
            if (dx * dx + dy * dy <= DOT_RADIUS_PX * DOT_RADIUS_PX * 4) {
                Vector3 bodyPos = bodyCenterIndex.get(dot.entityId);
                if (bodyPos != null) {
                    float dist = cameraPos.dst2(bodyPos);
                    if (dist < bestDistSq) {
                        bestDistSq = dist;
                        bestId = dot.entityId;
                    }
                }
            }
        }

        return bestId;
    }

    public void dispose() {
        shapeRenderer.dispose();
    }

    /**
     * 根据行星类型 ID 返回 RGB 颜色数组。
     */
    static float[] planetColor(String planetTypeId) {
        if (planetTypeId == null) {
            return new float[] { 0.55f, 0.47f, 0.38f };
        }
        return switch (planetTypeId.toUpperCase()) {
            case "GAS_GIANT" -> new float[] { 0.85f, 0.65f, 0.35f };
            case "OCEAN", "WATER" -> new float[] { 0.20f, 0.45f, 0.80f };
            case "ICE", "ICE_GIANT" -> new float[] { 0.75f, 0.82f, 0.90f };
            case "LAVA", "VOLCANIC" -> new float[] { 0.70f, 0.25f, 0.15f };
            case "DESERT" -> new float[] { 0.85f, 0.72f, 0.45f };
            case "GARDEN", "TERRAN" -> new float[] { 0.25f, 0.65f, 0.35f };
            default -> new float[] { 0.55f, 0.47f, 0.38f };
        };
    }
}