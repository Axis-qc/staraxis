package staraxis.render.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

import staraxis.game.astro.PlanetBody;
import staraxis.game.astro.StarBody;
import staraxis.game.astro.StarSystem;
import staraxis.game.entity.EntityType;
import staraxis.game.ship.ShipBody;
import staraxis.game.space.OrbitSolver;
import staraxis.game.space.OrbitalElements;
import staraxis.game.space.SpacePosition;
import staraxis.render.WorldCamera;
import staraxis.render.debug.ChunkGridDebugRenderer;
import staraxis.render.lod.LodCalculator;
import staraxis.render.lod.LodLevel;
import staraxis.render.mesh.OrbitRingMesh;
import staraxis.render.mesh.PlanetMesh;
import staraxis.render.mesh.ShipMesh;
import staraxis.render.mesh.StarMesh;
import staraxis.render.util.TemperatureColor;

/**
 * SystemViewRenderer（恒星系视图渲染器）。
 *
 * 渲染单个恒星系：所有恒星 + 行星 + 轨道环。
 * 恒星位置由 StarBody.systemPos 决定（单星在原点）。
 * 行星使用 OrbitSolver 实时计算位置，再加引力中心偏移。
 *
 * 三级 LOD：
 * - FULL：高精度球体 + 辉光 + 轨道环
 * - LOW：低精度球体，无辉光
 * - POINT：光点
 * - HIDDEN：不渲染
 */
public class SystemViewRenderer {

    private final ModelBatch modelBatch;
    private final Environment environment;
    private final DirectionalLight starLight;
    private final PlanetMesh planetMesh;
    private final StarMesh starMesh;
    private final OrbitRingMesh orbitRing;

    /** 舰船正方体网格。 */
    private final ShipMesh shipMesh;

    /** 舰船 ModelInstance 对象池（懒增长）。 */
    private final java.util.ArrayList<ModelInstance> shipInstances = new java.util.ArrayList<>();

    /** 对象池：恒星 ModelInstance（懒增长） */
    private final java.util.ArrayList<ModelInstance> starInstances = new java.util.ArrayList<>();
    /** 对象池：高精度行星 ModelInstance（懒增长） */
    private final java.util.ArrayList<ModelInstance> planetHighInstances = new java.util.ArrayList<>();
    /** 对象池：低精度行星 ModelInstance（懒增长） */
    private final java.util.ArrayList<ModelInstance> planetLowInstances = new java.util.ArrayList<>();

    /** 实体ID -> 天体在系统局部空间的位置查找表，每帧构建。包含恒星+行星+小行星+卫星。 */
    private final java.util.HashMap<Long, Vector3> bodyCenterIndex = new java.util.HashMap<>();

    private double simulationTime = 0.0;

    /** 区块网格调试渲染器（null = 关闭）。 */
    private ChunkGridDebugRenderer chunkDebug;

    /** 临时向量，避免每帧分配。 */
    private final Vector3 tmpVec = new Vector3();
    private final Vector3 tmpOffset = new Vector3();
    private final Vector3 tmpIntersect = new Vector3();
    private final Vector3 tmpScreenPos = new Vector3();

    /** 2D 行星圆标渲染器（叠加层，渲染太小的行星球体）。 */
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();

    /** 当前帧的行星 UI 圆标信息（每帧重建，供拾取用）。 */
    private final java.util.ArrayList<PlanetDotInfo> planetDotInfos = new java.util.ArrayList<>();

    /** 当前帧的舰船 UI 圆标信息（每帧重建，供拾取用）。 */
    private final java.util.ArrayList<ShipDotInfo> shipDotInfos = new java.util.ArrayList<>();

    /** 行星球体在屏幕上的直径小于此值时改用固定圆标标示位置。 */
    private static final float MIN_DIAMETER_PX = 20f;

    /** 屏幕圆标固定绘制半径（像素）。 */
    private static final float DOT_RADIUS_PX = MIN_DIAMETER_PX * 0.5f;

    /** 行星屏幕圆标信息（每帧重建）。 */
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

    /** 舰船屏幕圆标信息（每帧重建）。 */
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

    public SystemViewRenderer() {
        modelBatch = new ModelBatch();
        environment = new Environment();
        starLight = new DirectionalLight();
        starLight.set(0.8f, 0.8f, 0.9f, 0, 1, 0);
        environment.add(starLight);

        planetMesh = new PlanetMesh();
        starMesh = new StarMesh();
        orbitRing = new OrbitRingMesh();
        shipMesh = new ShipMesh();
    }

    /** 当前帧待渲染的舰船列表（由 ClientGame 每帧设置）。 */
    private java.util.List<ShipBody> currentFrameShips = java.util.List.of();

    /** 当前选中的舰船ID（高亮用）。 */
    private long highlightShipId = -1L;

    /** 确保恒星实例池足够大 */
    private void ensureStarInstances(int needed) {
        while (starInstances.size() < needed) {
            starInstances.add(new ModelInstance(starMesh.getModel(), 0, 0, 0));
        }
    }

    /** 确保行星两级实例池都足够大 */
    private void ensurePlanetInstances(int needed) {
        while (planetHighInstances.size() < needed) {
            planetHighInstances.add(new ModelInstance(planetMesh.getHighDetail(), 0, 0, 0));
        }
        while (planetLowInstances.size() < needed) {
            planetLowInstances.add(new ModelInstance(planetMesh.getLowDetail(), 0, 0, 0));
        }
    }

    /** 确保舰船实例池足够大 */
    private void ensureShipInstances(int needed) {
        while (shipInstances.size() < needed) {
            shipInstances.add(new ModelInstance(shipMesh.getModel(), 0, 0, 0));
        }
    }

    /**
     * 设置当前帧要渲染的舰船列表（由 ClientGame 在每帧渲染前调用）。
     */
    public void setShips(java.util.List<ShipBody> ships) {
        currentFrameShips = ships != null ? ships : java.util.List.of();
    }

    /**
     * 设置选中高亮舰船ID。-1 = 无选中。
     */
    public void setHighlightShip(long shipId) {
        this.highlightShipId = shipId;
    }

    /** 构建所有天体的系统局部空间位置索引（恒星→行星→小行星→卫星，按轨道层级顺序）。 */
    private void buildBodyIndex(StarSystem system) {
        bodyCenterIndex.clear();

        // 1. 恒星位于 systemPos
        for (StarBody star : system.stars) {
            bodyCenterIndex.put(star.entityId,
                new Vector3((float) star.systemPos.x(), (float) star.systemPos.y(), (float) star.systemPos.z()));
        }

        // 2. 行星/小行星：轨道解算 + 轨道中心偏移
        for (PlanetBody p : system.planets) {
            Vector3 center = bodyCenterIndex.get(p.orbitCenterEntityId);
            if (center == null) center = tmpVecZero();
            OrbitalElements orbit = toOrbitalElements(p);
            SpacePosition pos = OrbitSolver.solve(orbit, simulationTime);
            bodyCenterIndex.put(p.entityId, new Vector3(
                (float) pos.x() + center.x,
                (float) pos.y() + center.y,
                (float) pos.z() + center.z));
        }
        for (PlanetBody a : system.asteroids) {
            Vector3 center = bodyCenterIndex.get(a.orbitCenterEntityId);
            if (center == null) center = tmpVecZero();
            OrbitalElements orbit = toOrbitalElements(a);
            SpacePosition pos = OrbitSolver.solve(orbit, simulationTime);
            bodyCenterIndex.put(a.entityId, new Vector3(
                (float) pos.x() + center.x,
                (float) pos.y() + center.y,
                (float) pos.z() + center.z));
        }

        // 3. 卫星：轨道解算 + 母行星位置偏移
        for (PlanetBody m : system.moons) {
            Vector3 center = bodyCenterIndex.get(m.orbitCenterEntityId);
            if (center == null) center = tmpVecZero();
            OrbitalElements orbit = toOrbitalElements(m);
            SpacePosition pos = OrbitSolver.solve(orbit, simulationTime);
            bodyCenterIndex.put(m.entityId, new Vector3(
                (float) pos.x() + center.x,
                (float) pos.y() + center.y,
                (float) pos.z() + center.z));
        }
    }

    /** 返回 (0,0,0) 向量（临时用，每次调用返回同一对象，不跨帧持有）。 */
    private Vector3 tmpVecZero() {
        tmpVec.set(0, 0, 0);
        return tmpVec;
    }

    /** 从位置索引查找天体的系统局部空间位置。 */
    private boolean getBodyPosition(long entityId, Vector3 out) {
        Vector3 pos = bodyCenterIndex.get(entityId);
        if (pos != null) {
            out.set(pos);
            return true;
        }
        return false;
    }

    public void render(StarSystem system, WorldCamera camera) {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        // 构建所有天体的层级位置索引（恒星→行星→小行星→卫星）
        buildBodyIndex(system);

        // 第一遍：渲染所有恒星
        int starCount = system.stars.size();
        ensureStarInstances(starCount);
        renderAllStars(system, camera);

        // 第一遍：渲染所有行星 + 小行星 + 卫星，填充深度缓冲
        int bodyCount = system.planets.size() + system.asteroids.size() + system.moons.size();
        ensurePlanetInstances(bodyCount);
        int bodyIdx = 0;
        for (PlanetBody p : system.planets) { renderPlanetBody(bodyIdx++, p, camera); }
        for (PlanetBody a : system.asteroids) { renderPlanetBody(bodyIdx++, a, camera); }
        for (PlanetBody m : system.moons) { renderPlanetBody(bodyIdx++, m, camera); }

        // 渲染所有舰船（System View 内该星系的舰船）
        int shipCount = currentFrameShips.size();
        ensureShipInstances(shipCount);
        for (int i = 0; i < shipCount; i++) {
            renderShipMesh(i, currentFrameShips.get(i), camera);
        }

        // 第二遍：渲染所有轨道环，利用完整的深度缓冲实现正确遮挡
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        float orbitAlpha = LodCalculator.calculateOrbitAlpha(camera.getOrbitDistance());
        if (orbitAlpha > 0f) {
            // 所有天体（行星、小行星、卫星）的轨道环
            for (PlanetBody p : system.planets) { renderOrbitRing(p, camera, orbitAlpha); }
            for (PlanetBody a : system.asteroids) { renderOrbitRing(a, camera, orbitAlpha * 0.5f); }
            for (PlanetBody m : system.moons) { renderOrbitRing(m, camera, orbitAlpha * 0.4f); }
        }
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // 区块网格调试渲染（始终在最上层）
        if (chunkDebug != null) {
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
            chunkDebug.render(camera.camera.combined, camera.camera.position);
        }

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

        // 最上层：渲染行星 UI 圆标（深度测试已关闭）
        renderPlanetDots(system, camera);

        // 最上层：渲染舰船 UI 圆标
        renderShipDots(camera);
    }

    /** D.10+D.11: 渲染所有恒星，每颗按 systemPos 偏移 */
    private void renderAllStars(StarSystem system, WorldCamera camera) {
        for (int i = 0; i < system.stars.size(); i++) {
            StarBody star = system.stars.get(i);
            float[] rgb = TemperatureColor.temperatureToRgb(star.temperatureK);
            float scale = (float) star.radiusGU;

            ModelInstance instance = starInstances.get(i);
            instance.transform.idt();
            instance.transform.translate(
                    (float) star.systemPos.x(),
                    (float) star.systemPos.y(),
                    (float) star.systemPos.z());
            instance.transform.scl(scale);
            instance.materials.get(0).set(ColorAttribute.createDiffuse(rgb[0], rgb[1], rgb[2], 1f));

            modelBatch.begin(camera.camera);
            modelBatch.render(instance);
            modelBatch.end();
        }
    }

    /** 渲染单个天体（行星/小行星/卫星），位置从 bodyCenterIndex 取。 */
    private void renderPlanetBody(int index, PlanetBody body, WorldCamera camera) {
        // 从位置索引取系统局部坐标
        if (!getBodyPosition(body.entityId, tmpVec)) return;
        float px = tmpVec.x, py = tmpVec.y, pz = tmpVec.z;

        Vector3 cameraPos = camera.camera.position;
        double distance = Math.sqrt(
                (cameraPos.x - px) * (cameraPos.x - px) +
                        (cameraPos.y - py) * (cameraPos.y - py) +
                        (cameraPos.z - pz) * (cameraPos.z - pz));
        LodLevel lod = LodCalculator.calculate(distance);

        if (lod == LodLevel.HIDDEN) {
            return;
        }

        float scale = (float) body.radiusGU;
        float[] rgb = planetColor(body.planetTypeId);

        ModelInstance instance = (lod == LodLevel.LOW)
                ? planetLowInstances.get(index)
                : planetHighInstances.get(index);
        instance.transform.idt();
        instance.transform.translate(px, py, pz);
        instance.transform.scl(scale);
        instance.materials.get(0).set(ColorAttribute.createDiffuse(rgb[0], rgb[1], rgb[2], 1f));
        instance.materials.get(0)
                .set(ColorAttribute.createEmissive(rgb[0] * 0.08f, rgb[1] * 0.08f, rgb[2] * 0.08f, 1f));

        // 方向光：从轨道中心指向天体
        Vector3 centerPos = bodyCenterIndex.get(body.orbitCenterEntityId);
        if (centerPos != null) {
            starLight.setDirection(px - centerPos.x, py - centerPos.y, pz - centerPos.z);
        }

        modelBatch.begin(camera.camera);
        modelBatch.render(instance, environment);
        modelBatch.end();
    }

    /**
     * 渲染行星 UI 圆标（淡入淡出 LOD）。
     *
     * 基于相机 orbitDist 做 alpha 渐变，逻辑与轨道环 LOD 一致：
     *   orbitDist > 20000 → 完全不透明
     *   5000 < orbitDist < 20000 → 线性淡入
     *   orbitDist < 5000 → 完全透明（消失）
     * 每帧重建 planetDotInfos 供 pick() 做 2D 屏幕空间拾取。
     */
    private void renderPlanetDots(StarSystem system, WorldCamera camera) {
        planetDotInfos.clear();

        // 合并所有天体：行星 + 小行星 + 卫星
        java.util.ArrayList<PlanetBody> allBodies = new java.util.ArrayList<>();
        allBodies.addAll(system.planets);
        allBodies.addAll(system.asteroids);
        allBodies.addAll(system.moons);
        int count = allBodies.size();
        if (count == 0) return;

        // 计算圆标透明度
        float dotAlpha = LodCalculator.calculateDotAlpha(camera.getOrbitDistance());
        if (dotAlpha <= 0f) return;

        float gfxW = Gdx.graphics.getWidth();
        float gfxH = Gdx.graphics.getHeight();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(new Matrix4().setToOrtho(0f, gfxW, gfxH, 0f, -1f, 1f));
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        Vector3 camPos = camera.camera.position;

        for (PlanetBody body : allBodies) {
            // 从已预计算的位置索引取坐标
            Vector3 bodyPos = bodyCenterIndex.get(body.entityId);
            if (bodyPos == null) continue;

            float px = bodyPos.x, py = bodyPos.y, pz = bodyPos.z;

            // 跳过 HIDDEN
            double dist = Math.sqrt(
                    (camPos.x - px) * (camPos.x - px) +
                            (camPos.y - py) * (camPos.y - py) +
                            (camPos.z - pz) * (camPos.z - pz));
            if (LodCalculator.calculate(dist) == LodLevel.HIDDEN) continue;

            // 投影到屏幕坐标
            tmpScreenPos.set(px, py, pz);
            camera.camera.project(tmpScreenPos);
            if (tmpScreenPos.z < 0f || tmpScreenPos.z > 1f) continue;

            float dotY = gfxH - tmpScreenPos.y;

            // 小行星/卫星用更小的圆标
            float dotRadius = (body.entityType == EntityType.ASTEROID || body.entityType == EntityType.MOON)
                ? DOT_RADIUS_PX * 0.5f : DOT_RADIUS_PX;

            float[] rgb = planetColor(body.planetTypeId);
            shapeRenderer.setColor(rgb[0], rgb[1], rgb[2], dotAlpha);
            shapeRenderer.circle(tmpScreenPos.x, dotY, dotRadius);

            planetDotInfos.add(new PlanetDotInfo(body.entityId, tmpScreenPos.x, dotY));
        }

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** 渲染舰船 LOD 圆标（距离远时用圆圈替代立方体渲染） */
    private void renderShipDots(WorldCamera camera) {
        if (currentFrameShips.isEmpty()) return;

        shipDotInfos.clear();

        float gfxW = Gdx.graphics.getWidth();
        float gfxH = Gdx.graphics.getHeight();
        Vector3 camPos = camera.camera.position;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(new Matrix4().setToOrtho(0f, gfxW, gfxH, 0f, -1f, 1f));
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (ShipBody ship : currentFrameShips) {
            if (ship.posWorldGU == null) continue;

            float px = (float) ship.posWorldGU.x();
            float py = (float) ship.posWorldGU.y();
            float pz = (float) ship.posWorldGU.z();

            double dist = Math.sqrt(
                    (camPos.x - px) * (camPos.x - px) +
                    (camPos.y - py) * (camPos.y - py) +
                    (camPos.z - pz) * (camPos.z - pz));

            // 投影到屏幕坐标
            tmpScreenPos.set(px, py, pz);
            camera.camera.project(tmpScreenPos);
            if (tmpScreenPos.z < 0f || tmpScreenPos.z > 1f) continue;

            float dotY = gfxH - tmpScreenPos.y;

            // 根据距离决定圆圈透明度
            // < 500GU 全透明（立方体可见，不画圆圈遮挡）
            // 500~2000GU 线性淡入
            // > 2000GU 完全不透明
            float circleAlpha;
            if (dist < 500) {
                circleAlpha = 0f;
            } else if (dist > 2000) {
                circleAlpha = 0.9f;
            } else {
                circleAlpha = 0.9f * (float)((dist - 500) / 1500.0);
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

    /** 轨道环带偏移 */
    private void renderOrbitRing(PlanetBody planet, WorldCamera camera, float orbitAlpha) {
        OrbitalElements orbit = toOrbitalElements(planet);
        Vector3 center = bodyCenterIndex.get(planet.orbitCenterEntityId);
        float cx = 0, cy = 0, cz = 0;
        if (center != null) { cx = center.x; cy = center.y; cz = center.z; }

        orbitRing.render(orbit,
                cx, cy, cz,
                camera.camera.combined, new Color(0.3f, 0.4f, 0.6f, orbitAlpha));
    }

    /** 渲染舰船立方体 */
    private void renderShipMesh(int index, ShipBody ship, WorldCamera camera) {
        if (ship.posWorldGU == null) return;

        float px = (float) ship.posWorldGU.x();
        float py = (float) ship.posWorldGU.y();
        float pz = (float) ship.posWorldGU.z();

        ModelInstance instance = shipInstances.get(index);
        instance.transform.idt();
        instance.transform.translate(px, py, pz);
        instance.transform.scl(5f); // 边长 10 GU（半径为5）
        // 舰船颜色：选中为亮黄色，否则淡蓝色
        if (ship.entityId == highlightShipId) {
            instance.materials.get(0).set(ColorAttribute.createDiffuse(1.0f, 0.9f, 0.2f, 1f));
        } else {
            instance.materials.get(0).set(ColorAttribute.createDiffuse(0.4f, 0.6f, 1.0f, 1f));
        }

        modelBatch.begin(camera.camera);
        modelBatch.render(instance, environment);
        modelBatch.end();
    }

    public void advanceTime(double dtSeconds) {
        simulationTime += dtSeconds;
    }

    public void resetTime() {
        simulationTime = 0.0;
    }

    public void setSimulationTime(double time) {
        simulationTime = time;
    }

    /**
     * 开启或关闭区块网格调试渲染。
     * 开启后按 F4 可见 System View 中以原点为中心的区块边界线。
     */
    public void setDebugChunksEnabled(boolean enabled) {
        if (enabled && chunkDebug == null) {
            chunkDebug = new ChunkGridDebugRenderer();
        } else if (!enabled) {
            if (chunkDebug != null) {
                chunkDebug.dispose();
                chunkDebug = null;
            }
        }
    }

    public boolean isDebugChunksEnabled() {
        return chunkDebug != null;
    }

    public void dispose() {
        modelBatch.dispose();
        planetMesh.dispose();
        starMesh.dispose();
        orbitRing.dispose();
        shipMesh.dispose();
        shapeRenderer.dispose();
        if (chunkDebug != null) {
            chunkDebug.dispose();
            chunkDebug = null;
        }
    }

    /** D.15: 写入实际的 longitudeOfAscendingNode 和 epoch */
    private static OrbitalElements toOrbitalElements(PlanetBody p) {
        double periodSeconds = p.orbitalPeriodDays * 86400.0;
        return new OrbitalElements(
                p.semiMajorAxisGU,
                p.eccentricity,
                Math.toRadians(p.inclinationDeg),
                Math.toRadians(p.longitudeOfAscendingNodeDeg),
                Math.toRadians(p.periapsisArgDeg),
                Math.toRadians(p.meanAnomalyDegAtEpoch),
                0.0,
                periodSeconds);
    }

    /**
     * 拾取 System View 中屏幕坐标处的天体（恒星或行星）。
     *
     * @param camera  当前相机
     * @param screenX 屏幕 X 坐标
     * @param screenY 屏幕 Y 坐标
     * @param system  当前渲染的恒星系
     * @return 最近天体的 entityId，未命中返回 -1
     */
    public long pick(WorldCamera camera, int screenX, int screenY, StarSystem system) {
        Ray ray = camera.camera.getPickRay(screenX, screenY);
        long closestId = -1;
        float closestDist = Float.MAX_VALUE;

        // 检测所有恒星
        for (StarBody star : system.stars) {
            tmpVec.set((float) star.systemPos.x(), (float) star.systemPos.y(), (float) star.systemPos.z());
            float radius = (float) star.radiusGU;
            if (Intersector.intersectRaySphere(ray, tmpVec, radius, tmpIntersect)) {
                float dist = tmpIntersect.dst2(ray.origin);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestId = star.entityId;
                }
            }
        }

        // 检测所有行星 + 小行星 + 卫星（使用预计算的位置索引）
        java.util.ArrayList<PlanetBody> allBodies = new java.util.ArrayList<>();
        allBodies.addAll(system.planets);
        allBodies.addAll(system.asteroids);
        allBodies.addAll(system.moons);
        for (PlanetBody body : allBodies) {
            Vector3 bodyPos = bodyCenterIndex.get(body.entityId);
            if (bodyPos == null) continue;
            float radius = (float) body.radiusGU;
            if (Intersector.intersectRaySphere(ray, bodyPos, radius, tmpIntersect)) {
                float dist = tmpIntersect.dst2(ray.origin);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestId = body.entityId;
                }
            }
        }

        // 检测 2D 天体圆标（屏幕空间）
        for (PlanetDotInfo dot : planetDotInfos) {
            float dx = screenX - dot.screenX;
            float dy = screenY - dot.screenY;
            if (dx * dx + dy * dy <= DOT_RADIUS_PX * DOT_RADIUS_PX * 4) {
                // 命中圆标，用预计算位置的 3D 距离排序
                Vector3 bodyPos = bodyCenterIndex.get(dot.entityId);
                if (bodyPos != null) {
                    float dist = camera.camera.position.dst2(bodyPos);
                    if (dist < closestDist) {
                        closestDist = dist;
                        closestId = dot.entityId;
                    }
                }
            }
        }

        // 检测所有舰船（使用当前位置）
        for (ShipBody ship : currentFrameShips) {
            if (ship.posWorldGU == null) continue;
            tmpOffset.set((float) ship.posWorldGU.x(), (float) ship.posWorldGU.y(), (float) ship.posWorldGU.z());
            float radius = 5f; // 10GU 边长半边长
            if (Intersector.intersectRaySphere(ray, tmpOffset, radius, tmpIntersect)) {
                float dist = tmpIntersect.dst2(ray.origin);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestId = ship.entityId;
                }
            }
        }

        // 检测 2D 舰船圆标（屏幕空间）
        for (ShipDotInfo dot : shipDotInfos) {
            float dx = screenX - dot.screenX;
            float dy = screenY - dot.screenY;
            if (dx * dx + dy * dy <= (DOT_RADIUS_PX * 0.6f) * (DOT_RADIUS_PX * 0.6f)) {
                for (ShipBody ship : currentFrameShips) {
                    if (ship.entityId == dot.entityId && ship.posWorldGU != null) {
                        float dist = camera.camera.position.dst2(
                            (float) ship.posWorldGU.x(),
                            (float) ship.posWorldGU.y(),
                            (float) ship.posWorldGU.z());
                        if (dist < closestDist) {
                            closestDist = dist;
                            closestId = dot.entityId;
                        }
                        break;
                    }
                }
            }
        }

        return closestId;
    }

    /**
     * 获取天体当前在系统局部空间中的位置（用于镜头聚焦）。
     *
     * @param entityId 目标实体 ID
     * @param system   当前恒星系
     * @param out      输出位置（系统局部坐标）
     * @return 找到返回 true，未找到返回 false
     */
    public boolean getBodyPosition(long entityId, StarSystem system, Vector3 out) {
        // 检查恒星
        for (StarBody star : system.stars) {
            if (star.entityId == entityId) {
                out.set((float) star.systemPos.x(), (float) star.systemPos.y(), (float) star.systemPos.z());
                return true;
            }
        }
        // 从 bodyCenterIndex 查找（包含恒星、行星、小行星、卫星）
        Vector3 cached = bodyCenterIndex.get(entityId);
        if (cached != null) {
            out.set(cached);
            return true;
        }
        // 检查舰船
        for (ShipBody ship : currentFrameShips) {
            if (ship.entityId == entityId && ship.posWorldGU != null) {
                out.set((float) ship.posWorldGU.x(), (float) ship.posWorldGU.y(), (float) ship.posWorldGU.z());
                return true;
            }
        }
        return false;
    }

    private static float[] planetColor(String planetTypeId) {
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
