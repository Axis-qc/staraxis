package staraxis.render.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import staraxis.game.astro.PlanetBody;
import staraxis.game.astro.StarSystem;
import staraxis.game.entity.EntityType;
import staraxis.game.ship.ShipBody;
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
 */
public class SystemViewOverlay {

    private final ShapeRenderer shapeRenderer = new ShapeRenderer();

    private final List<PlanetDotInfo> planetDotInfos = new ArrayList<>();
    private final List<ShipDotInfo> shipDotInfos = new ArrayList<>();

    private static final float DOT_RADIUS_PX = 10f;

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

    private static class ShipDotInfo {
        final long entityId;
        final float screenX;
        final float screenY;

        ShipDotInfo(long id, float x, float y) {
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
    public void renderPlanetDots(StarSystem system, WorldCamera camera,
                                  Map<Long, Vector3> bodyCenterIndex) {
        planetDotInfos.clear();

        List<PlanetBody> allBodies = new ArrayList<>();
        allBodies.addAll(system.planets);
        allBodies.addAll(system.asteroids);
        allBodies.addAll(system.moons);
        int count = allBodies.size();
        if (count == 0)
            return;

        float dotAlpha = LodCalculator.calculateDotAlpha(camera.getOrbitDistance());
        if (dotAlpha <= 0f)
            return;

        float gfxW = Gdx.graphics.getWidth();
        float gfxH = Gdx.graphics.getHeight();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(new Matrix4().setToOrtho(0f, gfxW, gfxH, 0f, -1f, 1f));
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        Vector3 camPos = camera.camera.position;

        for (PlanetBody body : allBodies) {
            Vector3 bodyPos = bodyCenterIndex.get(body.entityId);
            if (bodyPos == null)
                continue;

            float px = bodyPos.x, py = bodyPos.y, pz = bodyPos.z;

            double dist = Math.sqrt(
                    (camPos.x - px) * (camPos.x - px) +
                            (camPos.y - py) * (camPos.y - py) +
                            (camPos.z - pz) * (camPos.z - pz));
            if (LodCalculator.calculate(dist) == LodLevel.HIDDEN)
                continue;

            tmpScreenPos.set(px, py, pz);
            camera.camera.project(tmpScreenPos);
            if (tmpScreenPos.z < 0f || tmpScreenPos.z > 1f)
                continue;

            float dotY = gfxH - tmpScreenPos.y;

            float dotRadius = (body.entityType == EntityType.ASTEROID || body.entityType == EntityType.MOON)
                    ? DOT_RADIUS_PX * 0.5f
                    : DOT_RADIUS_PX;

            float[] rgb = planetColor(body.planetTypeId);
            shapeRenderer.setColor(rgb[0], rgb[1], rgb[2], dotAlpha);
            shapeRenderer.circle(tmpScreenPos.x, dotY, dotRadius);

            planetDotInfos.add(new PlanetDotInfo(body.entityId, tmpScreenPos.x, dotY));
        }

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * 渲染舰船 LOD 圆标（距离远时用圆圈替代立方体渲染）。
     */
    public void renderShipDots(WorldCamera camera, List<ShipBody> currentFrameShips) {
        if (currentFrameShips.isEmpty())
            return;

        shipDotInfos.clear();

        float gfxW = Gdx.graphics.getWidth();
        float gfxH = Gdx.graphics.getHeight();
        Vector3 camPos = camera.camera.position;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(new Matrix4().setToOrtho(0f, gfxW, gfxH, 0f, -1f, 1f));
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (ShipBody ship : currentFrameShips) {
            if (ship.posWorldGU == null)
                continue;

            float px = (float) ship.posWorldGU.x();
            float py = (float) ship.posWorldGU.y();
            float pz = (float) ship.posWorldGU.z();

            double dist = Math.sqrt(
                    (camPos.x - px) * (camPos.x - px) +
                            (camPos.y - py) * (camPos.y - py) +
                            (camPos.z - pz) * (camPos.z - pz));

            tmpScreenPos.set(px, py, pz);
            camera.camera.project(tmpScreenPos);
            if (tmpScreenPos.z < 0f || tmpScreenPos.z > 1f)
                continue;

            float dotY = gfxH - tmpScreenPos.y;

            float circleAlpha;
            if (dist < 500) {
                circleAlpha = 0f;
            } else if (dist > 2000) {
                circleAlpha = 0.9f;
            } else {
                circleAlpha = 0.9f * (float) ((dist - 500) / 1500.0);
            }

            if (circleAlpha > 0.01f) {
                shapeRenderer.setColor(0.4f, 0.6f, 1.0f, circleAlpha);
                shapeRenderer.circle(tmpScreenPos.x, dotY, DOT_RADIUS_PX * 0.6f);
            }

            shipDotInfos.add(new ShipDotInfo(ship.entityId, tmpScreenPos.x, dotY));
        }

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * 2D 圆标拾取。返回命中的最佳实体 ID，无命中返回 -1。
     * 调用方应将其与 3D 拾取结果比较距离后取最近者。
     */
    public long pickDots(int screenX, int screenY, long currentBestId, float currentBestDistSq,
                          Map<Long, Vector3> bodyCenterIndex, List<ShipBody> currentFrameShips,
                          Vector3 cameraPos) {
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

        for (ShipDotInfo dot : shipDotInfos) {
            float dx = screenX - dot.screenX;
            float dy = screenY - dot.screenY;
            if (dx * dx + dy * dy <= (DOT_RADIUS_PX * 0.6f) * (DOT_RADIUS_PX * 0.6f)) {
                for (ShipBody ship : currentFrameShips) {
                    if (ship.entityId == dot.entityId && ship.posWorldGU != null) {
                        float dist = cameraPos.dst2(
                                (float) ship.posWorldGU.x(),
                                (float) ship.posWorldGU.y(),
                                (float) ship.posWorldGU.z());
                        if (dist < bestDistSq) {
                            bestDistSq = dist;
                            bestId = dot.entityId;
                        }
                        break;
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