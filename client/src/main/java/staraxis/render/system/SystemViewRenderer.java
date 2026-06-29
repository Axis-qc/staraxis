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
import staraxis.game.space.OrbitalElements;
import staraxis.game.space.OrbitSolver;
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
 * 渲染单个恒星系：主恒星 + 行星 + 轨道环。
 * 恒星在 (0,0,0)，行星使用 OrbitSolver 实时计算位置。
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

    private double simulationTime = 0.0;

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

    public void render(StarSystem system, WorldCamera camera) {
        Vector3 cameraPos = camera.camera.position;

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        Vector3 starPos = new Vector3(0, 0, 0);
        if (!system.stars.isEmpty()) {
            starPos = renderMainStar(system.stars.get(0), camera);
        }

        for (PlanetBody planet : system.planets) {
            renderPlanet(planet, starPos, camera);
        }

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    private Vector3 renderMainStar(StarBody star, WorldCamera camera) {
        double distance = camera.camera.position.len();
        LodLevel lod = LodCalculator.calculate(distance);

        float[] rgb = TemperatureColor.temperatureToRgb(star.temperatureK);
        float scale = (float) star.radiusGU;

        ModelInstance instance = new ModelInstance(starMesh.getModel(), 0, 0, 0);
        instance.transform.scl(scale);
        instance.materials.get(0).set(ColorAttribute.createDiffuse(rgb[0], rgb[1], rgb[2], 1f));
        instance.materials.get(0).set(ColorAttribute.createEmissive(rgb[0], rgb[1], rgb[2], 1f));

        modelBatch.begin(camera.camera);
        modelBatch.render(instance); // 不传 environment，方向光不影响恒星
        modelBatch.end();

        return new Vector3(0, 0, 0);
    }

    private void renderPlanet(PlanetBody planet, Vector3 starPos, WorldCamera camera) {
        OrbitalElements orbit = toOrbitalElements(planet);
        SpacePosition pos = OrbitSolver.solve(orbit, simulationTime);
        float px = (float) pos.x();
        float py = (float) pos.y();
        float pz = (float) pos.z();

        Vector3 cameraPos = camera.camera.position;
        double distance = Math.sqrt(
            (cameraPos.x - px) * (cameraPos.x - px) +
            (cameraPos.y - py) * (cameraPos.y - py) +
            (cameraPos.z - pz) * (cameraPos.z - pz)
        );
        LodLevel lod = LodCalculator.calculate(distance);

        if (lod == LodLevel.HIDDEN) {
            return;
        }

        if (lod == LodLevel.FULL) {
            orbitRing.render(orbit, camera.camera.combined, new Color(0.3f, 0.4f, 0.6f, 0.3f));
        }

        float scale = (float) planet.radiusGU;
        float[] rgb = planetColor(planet.planetTypeId);

        ModelInstance instance;
        if (lod == LodLevel.FULL) {
            instance = new ModelInstance(planetMesh.getHighDetail(), px, py, pz);
        } else {
            instance = new ModelInstance(planetMesh.getLowDetail(), px, py, pz);
        }

        instance.transform.scl(scale);
        instance.materials.get(0).set(ColorAttribute.createDiffuse(rgb[0], rgb[1], rgb[2], 1f));
        instance.materials.get(0).set(ColorAttribute.createEmissive(rgb[0] * 0.08f, rgb[1] * 0.08f, rgb[2] * 0.08f, 1f));

        starLight.setDirection(px - starPos.x, py - starPos.y, pz - starPos.z);

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

    public void resize(int w, int h) {
    }

    public void dispose() {
        modelBatch.dispose();
        planetMesh.dispose();
        starMesh.dispose();
        orbitRing.dispose();
    }

    private static OrbitalElements toOrbitalElements(PlanetBody p) {
        double periodSeconds = p.orbitalPeriodDays * 86400.0;
        return new OrbitalElements(
            p.semiMajorAxisGU,
            p.eccentricity,
            Math.toRadians(p.inclinationDeg),
            0.0,
            Math.toRadians(p.periapsisArgDeg),
            Math.toRadians(p.meanAnomalyDegAtEpoch),
            0.0,
            periodSeconds
        );
    }

    private static float[] planetColor(String planetTypeId) {
        if (planetTypeId == null) {
            return new float[]{0.55f, 0.47f, 0.38f};
        }
        return switch (planetTypeId.toUpperCase()) {
            case "GAS_GIANT" -> new float[]{0.85f, 0.65f, 0.35f};
            case "OCEAN", "WATER" -> new float[]{0.20f, 0.45f, 0.80f};
            case "ICE", "ICE_GIANT" -> new float[]{0.75f, 0.82f, 0.90f};
            case "LAVA", "VOLCANIC" -> new float[]{0.70f, 0.25f, 0.15f};
            case "DESERT" -> new float[]{0.85f, 0.72f, 0.45f};
            case "GARDEN", "TERRAN" -> new float[]{0.25f, 0.65f, 0.35f};
            default -> new float[]{0.55f, 0.47f, 0.38f};
        };
    }
}
