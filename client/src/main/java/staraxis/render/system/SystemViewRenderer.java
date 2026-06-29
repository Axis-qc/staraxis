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

import staraxis.game.space.OrbitSolver;
import staraxis.game.space.SpacePosition;
import staraxis.game.space.galaxy.SpectralType;
import staraxis.game.space.galaxy.StarPosition;
import staraxis.game.space.system.PlanetData;
import staraxis.game.space.system.StarSystemData;
import staraxis.render.WorldCamera;
import staraxis.render.lod.LodCalculator;
import staraxis.render.lod.LodLevel;
import staraxis.render.mesh.OrbitRingMesh;
import staraxis.render.mesh.PlanetMesh;
import staraxis.render.mesh.StarMesh;

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
    private final PlanetMesh planetMesh;
    private final StarMesh starMesh;
    private final OrbitRingMesh orbitRing;

    /** 系统内模拟时间（游戏秒），驱动行星公转。 */
    private double simulationTime = 0.0;

    public SystemViewRenderer() {
        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.9f, 0, 1, 0));

        planetMesh = new PlanetMesh();
        starMesh = new StarMesh();
        orbitRing = new OrbitRingMesh();
    }

    /**
     * 渲染恒星系。
     *
     * @param system 恒星系数据
     * @param camera 世界相机
     */
    public void render(StarSystemData system, WorldCamera camera) {
        Vector3 cameraPos = camera.camera.position;

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        // 渲染主恒星（在原点）
        renderMainStar(system.mainStar, cameraPos, camera);

        // 渲染行星
        for (PlanetData planet : system.planets) {
            renderPlanet(planet, cameraPos, camera);
        }

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    /**
     * 渲染主恒星（在原点）。
     */
    private void renderMainStar(StarPosition star, Vector3 cameraPos, WorldCamera camera) {
        double distance = cameraPos.len();
        LodLevel lod = LodCalculator.calculate(distance);

        if (lod == LodLevel.HIDDEN) {
            return;
        }

        SpectralType type = star.spectralType();
        float scale = (float) star.radiusGU();

        ModelInstance instance = new ModelInstance(starMesh.getModel(), 0, 0, 0);
        instance.transform.scl(scale);
        instance.materials.get(0).set(ColorAttribute.createDiffuse(
            type.colorR, type.colorG, type.colorB, 1f));

        modelBatch.begin(camera.camera);
        modelBatch.render(instance, environment);
        modelBatch.end();
    }

    /**
     * 渲染行星。
     */
    private void renderPlanet(PlanetData planet, Vector3 cameraPos, WorldCamera camera) {
        // 计算行星当前位置
        SpacePosition pos = OrbitSolver.solve(planet.orbit(), simulationTime);
        float px = (float) pos.x();
        float py = (float) pos.y();
        float pz = (float) pos.z();

        // 计算 LOD
        double distance = Math.sqrt(
            (cameraPos.x - px) * (cameraPos.x - px) +
            (cameraPos.y - py) * (cameraPos.y - py) +
            (cameraPos.z - pz) * (cameraPos.z - pz)
        );
        LodLevel lod = LodCalculator.calculate(distance);

        if (lod == LodLevel.HIDDEN) {
            return;
        }

        // 渲染轨道环（仅 FULL LOD）
        if (lod == LodLevel.FULL) {
            orbitRing.render(planet.orbit(), camera.camera.combined, new Color(0.3f, 0.4f, 0.6f, 0.3f));
        }

        // 渲染行星球体
        float scale = (float) planet.radiusGU();

        ModelInstance instance;
        if (lod == LodLevel.FULL) {
            instance = new ModelInstance(planetMesh.getHighDetail(), px, py, pz);
        } else {
            instance = new ModelInstance(planetMesh.getLowDetail(), px, py, pz);
        }

        instance.transform.scl(scale);
        instance.materials.get(0).set(ColorAttribute.createDiffuse(
            planet.colorR(), planet.colorG(), planet.colorB(), 1f));

        modelBatch.begin(camera.camera);
        modelBatch.render(instance, environment);
        modelBatch.end();
    }

    /**
     * 推进模拟时间。
     *
     * @param dtSeconds 经过的游戏秒数
     */
    public void advanceTime(double dtSeconds) {
        simulationTime += dtSeconds;
    }

    /**
     * 重置模拟时间。
     */
    public void resetTime() {
        simulationTime = 0.0;
    }

    /**
     * 设置模拟时间。
     */
    public void setSimulationTime(double time) {
        simulationTime = time;
    }

    public void resize(int w, int h) {
        // ModelBatch 不需要 resize
    }

    public void dispose() {
        modelBatch.dispose();
        planetMesh.dispose();
        starMesh.dispose();
        orbitRing.dispose();
    }
}
