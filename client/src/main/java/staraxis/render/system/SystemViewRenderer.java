package staraxis.render.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.Vector3;

import staraxis.game.astro.PlanetBody;
import staraxis.game.astro.StarBody;
import staraxis.game.astro.StarSystem;
import staraxis.game.space.OrbitSolver;
import staraxis.game.space.OrbitalElements;
import staraxis.game.space.SpacePosition;
import staraxis.render.WorldCamera;
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

    /** 临时向量，避免每帧分配。 */
    private final Vector3 tmpVec = new Vector3();
    private final Vector3 tmpOffset = new Vector3();

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

        double orbitDist = camera.getOrbitDistance();
        float orbitAlpha;
        if (orbitDist > 20000) {
            orbitAlpha = 0.9f;
        } else if (orbitDist > 5000) {
            orbitAlpha = 0.9f * (float) ((orbitDist - 5000) / 15000.0);
        } else {
            orbitAlpha = 0f;
        }
        if (orbitAlpha > 0f) {
            for (int i = 0; i < planetCount; i++) {
                renderOrbitRing(system.planets.get(i), camera, orbitAlpha);
            }
        }
        Gdx.gl.glDisable(GL20.GL_BLEND);

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
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

    public void dispose() {
        modelBatch.dispose();
        planetMesh.dispose();
        starMesh.dispose();
        orbitRing.dispose();
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
