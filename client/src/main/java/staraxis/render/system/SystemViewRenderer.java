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
import staraxis.game.space.OrbitSolver;
import staraxis.game.space.OrbitalElements;
import staraxis.game.space.SpacePosition;
import staraxis.render.WorldCamera;
import staraxis.render.debug.ChunkGridDebugRenderer;
import staraxis.render.lod.LodCalculator;
import staraxis.render.lod.LodLevel;
import staraxis.render.mesh.OrbitRingMesh;
import staraxis.render.mesh.PlanetMesh;
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

    /** 对象池：恒星 ModelInstance（懒增长） */
    private final java.util.ArrayList<ModelInstance> starInstances = new java.util.ArrayList<>();
    /** 对象池：高精度行星 ModelInstance（懒增长） */
    private final java.util.ArrayList<ModelInstance> planetHighInstances = new java.util.ArrayList<>();
    /** 对象池：低精度行星 ModelInstance（懒增长） */
    private final java.util.ArrayList<ModelInstance> planetLowInstances = new java.util.ArrayList<>();

    /** 实体ID -> StarBody 查找表，每帧构建。 */
    private final java.util.HashMap<Long, StarBody> starIndex = new java.util.HashMap<>();

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

    public SystemViewRenderer() {
        modelBatch = new ModelBatch();
        environment = new Environment();
        starLight = new DirectionalLight();
        starLight.set(0.8f, 0.8f, 0.9f, 0, 1, 0);
        environment.add(starLight);

        planetMesh = new PlanetMesh();
        starMesh = new StarMesh();
        orbitRing = new OrbitRingMesh();
    }

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

    /** 构建恒星 ID 索引 */
    private void buildStarIndex(StarSystem system) {
        starIndex.clear();
        for (StarBody star : system.stars) {
            starIndex.put(star.entityId, star);
        }
    }

    /** 查找引力中心恒星在系统空间中的位置（SpacePosition -> float x/y/z），无则返回原点 */
    private void getOrbitCenterPos(PlanetBody planet, Vector3 out) {
        StarBody center = starIndex.get(planet.orbitCenterEntityId);
        if (center != null) {
            out.set((float) center.systemPos.x(), (float) center.systemPos.y(), (float) center.systemPos.z());
        } else {
            out.set(0, 0, 0);
        }
    }

    public void render(StarSystem system, WorldCamera camera) {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        // 构建查找索引
        buildStarIndex(system);

        // 第一遍：渲染所有恒星
        int starCount = system.stars.size();
        ensureStarInstances(starCount);
        renderAllStars(system, camera);

        // 第一遍：渲染所有行星，填充深度缓冲
        int planetCount = system.planets.size();
        ensurePlanetInstances(planetCount);
        for (int i = 0; i < planetCount; i++) {
            renderPlanetMesh(i, system.planets.get(i), camera);
        }

        // 第二遍：渲染所有轨道环，利用完整的深度缓冲实现正确遮挡
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        float orbitAlpha = LodCalculator.calculateOrbitAlpha(camera.getOrbitDistance());
        if (orbitAlpha > 0f) {
            for (int i = 0; i < planetCount; i++) {
                renderOrbitRing(system.planets.get(i), camera, orbitAlpha);
            }
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

    /** 行星按引力中心恒星偏移渲染 */
    private void renderPlanetMesh(int index, PlanetBody planet, WorldCamera camera) {
        OrbitalElements orbit = toOrbitalElements(planet);
        SpacePosition pos = OrbitSolver.solve(orbit, simulationTime);

        // 查引力中心偏移，一次 lookup 供偏移 + 方向光共用
        getOrbitCenterPos(planet, tmpOffset);
        float px = (float) pos.x() + tmpOffset.x;
        float py = (float) pos.y() + tmpOffset.y;
        float pz = (float) pos.z() + tmpOffset.z;

        Vector3 cameraPos = camera.camera.position;
        double distance = Math.sqrt(
                (cameraPos.x - px) * (cameraPos.x - px) +
                        (cameraPos.y - py) * (cameraPos.y - py) +
                        (cameraPos.z - pz) * (cameraPos.z - pz));
        LodLevel lod = LodCalculator.calculate(distance);

        if (lod == LodLevel.HIDDEN) {
            return;
        }

        float scale = (float) planet.radiusGU;
        float[] rgb = planetColor(planet.planetTypeId);

        ModelInstance instance = (lod == LodLevel.LOW)
                ? planetLowInstances.get(index)
                : planetHighInstances.get(index);
        instance.transform.idt();
        instance.transform.translate(px, py, pz);
        instance.transform.scl(scale);
        instance.materials.get(0).set(ColorAttribute.createDiffuse(rgb[0], rgb[1], rgb[2], 1f));
        instance.materials.get(0)
                .set(ColorAttribute.createEmissive(rgb[0] * 0.08f, rgb[1] * 0.08f, rgb[2] * 0.08f, 1f));

        // 方向光：从行星绕的恒星指向行星
        if (planet.orbitCenterEntityId != 0L) {
            starLight.setDirection(
                    px - tmpOffset.x,
                    py - tmpOffset.y,
                    pz - tmpOffset.z);
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

        int count = system.planets.size();
        if (count == 0) return;

        // 计算圆标透明度（由 LodCalculator 统一管理 LOD 参数）
        float dotAlpha = LodCalculator.calculateDotAlpha(camera.getOrbitDistance());
        if (dotAlpha <= 0f) {
            // 完全透明时不绘制也不拾取
            return;
        }

        float gfxW = Gdx.graphics.getWidth();
        float gfxH = Gdx.graphics.getHeight();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(new Matrix4().setToOrtho(0f, gfxW, gfxH, 0f, -1f, 1f));
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        Vector3 camPos = camera.camera.position;

        for (int i = 0; i < count; i++) {
            PlanetBody planet = system.planets.get(i);

            // 计算世界坐标
            OrbitalElements orbit = toOrbitalElements(planet);
            SpacePosition pos = OrbitSolver.solve(orbit, simulationTime);
            getOrbitCenterPos(planet, tmpVec);
            float px = (float) pos.x() + tmpVec.x;
            float py = (float) pos.y() + tmpVec.y;
            float pz = (float) pos.z() + tmpVec.z;

            // 跳过 HIDDEN 的行星
            double dist = Math.sqrt(
                    (camPos.x - px) * (camPos.x - px) +
                            (camPos.y - py) * (camPos.y - py) +
                            (camPos.z - pz) * (camPos.z - pz));
            if (LodCalculator.calculate(dist) == LodLevel.HIDDEN) continue;

            // 投影到屏幕坐标
            tmpScreenPos.set(px, py, pz);
            camera.camera.project(tmpScreenPos);
            if (tmpScreenPos.z < 0f || tmpScreenPos.z > 1f) continue;

            // 翻转 Y：project() 左下角原点 → 左上角原点（与 Gdx.input 一致）
            float dotY = gfxH - tmpScreenPos.y;

            // 绘制固定大小彩色圆形（带 alpha 渐变）
            float[] rgb = planetColor(planet.planetTypeId);
            shapeRenderer.setColor(rgb[0], rgb[1], rgb[2], dotAlpha);
            shapeRenderer.circle(tmpScreenPos.x, dotY, DOT_RADIUS_PX);

            // 记录供拾取
            planetDotInfos.add(new PlanetDotInfo(planet.entityId, tmpScreenPos.x, dotY));
        }

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** 轨道环带偏移 */
    private void renderOrbitRing(PlanetBody planet, WorldCamera camera, float orbitAlpha) {
        OrbitalElements orbit = toOrbitalElements(planet);
        getOrbitCenterPos(planet, tmpVec);

        orbitRing.render(orbit,
                tmpVec.x, tmpVec.y, tmpVec.z,
                camera.camera.combined, new Color(0.3f, 0.4f, 0.6f, orbitAlpha));
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

        // 检测所有行星（使用当前轨道位置）
        for (PlanetBody planet : system.planets) {
            OrbitalElements orbit = toOrbitalElements(planet);
            SpacePosition pos = OrbitSolver.solve(orbit, simulationTime);
            getOrbitCenterPos(planet, tmpVec);
            tmpOffset.set((float) pos.x() + tmpVec.x, (float) pos.y() + tmpVec.y, (float) pos.z() + tmpVec.z);
            float radius = (float) planet.radiusGU;
            if (Intersector.intersectRaySphere(ray, tmpOffset, radius, tmpIntersect)) {
                float dist = tmpIntersect.dst2(ray.origin);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestId = planet.entityId;
                }
            }
        }

        // 检测 2D 行星圆标（屏幕空间）
        for (PlanetDotInfo dot : planetDotInfos) {
            float dx = screenX - dot.screenX;
            float dy = screenY - dot.screenY;
            if (dx * dx + dy * dy <= DOT_RADIUS_PX * DOT_RADIUS_PX) {
                // 命中圆标，用该行星的 3D 距离作为深度排序（与 3D 命中比较）
                for (PlanetBody planet : system.planets) {
                    if (planet.entityId == dot.entityId) {
                        OrbitalElements orbit = toOrbitalElements(planet);
                        SpacePosition pPos = OrbitSolver.solve(orbit, simulationTime);
                        getOrbitCenterPos(planet, tmpVec);
                        float px = (float) pPos.x() + tmpVec.x;
                        float py = (float) pPos.y() + tmpVec.y;
                        float pz = (float) pPos.z() + tmpVec.z;
                        float dist = camera.camera.position.dst2(px, py, pz);
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
        // 检查行星
        for (PlanetBody planet : system.planets) {
            if (planet.entityId == entityId) {
                OrbitalElements orbit = toOrbitalElements(planet);
                SpacePosition pos = OrbitSolver.solve(orbit, simulationTime);
                getOrbitCenterPos(planet, out);
                out.x += (float) pos.x();
                out.y += (float) pos.y();
                out.z += (float) pos.z();
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
