package staraxis.render.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

import staraxis.game.astro.PlanetBody;
import staraxis.game.astro.StarBody;
import staraxis.game.astro.StarSystem;
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

    /** 2D 屏幕圆标叠加层（天体/舰船位置标记 + 拾取）。 */
    private final SystemViewOverlay overlay = new SystemViewOverlay();

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

        // 最上层：2D 屏幕圆标叠加层（深度测试已关闭）
        overlay.renderPlanetDots(system, camera, bodyCenterIndex);
        overlay.renderShipDots(camera, currentFrameShips);
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
        float[] rgb = SystemViewOverlay.planetColor(body.planetTypeId);

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
        overlay.dispose();
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

        // 2D 圆标拾取委托给 overlay
        closestId = overlay.pickDots(screenX, screenY, closestId, closestDist,
                bodyCenterIndex, currentFrameShips, camera.camera.position);

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
}
