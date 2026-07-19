package staraxis.render.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

import staraxis.game.entity.EntityType;
import staraxis.game.space.SpacePosition;
import staraxis.game.space.galaxy.GalaxyConfig;
import staraxis.game.space.galaxy.GalaxyData;
import staraxis.game.space.galaxy.GalaxyGenerator;
import staraxis.game.space.galaxy.GalaxyGeneratorFactory;
import staraxis.game.space.galaxy.SpectralType;
import staraxis.game.space.galaxy.StarPosition;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.game.state.snapshot.EntitySnapshot.StarDetails;
import staraxis.render.SkyboxRenderer;
import staraxis.render.WorldCamera;
import staraxis.render.galaxy.StarBatchRenderer;
import staraxis.render.galaxy.StarHaloRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * MenuGalaxyBackground（主菜单银河背景）。
 *
 * 在主菜单页面显示一个缓慢自转的 3D 银河系，替代原来的 2D StarfieldBackground。
 * 使用与游戏内一致的 GalaxyGenerator + StarBatchRenderer + StarHaloRenderer，
 * 确保视觉效果一致，且启动快速（纯坐标生成，无需完整世界生成）。
 *
 * 不依赖 StarAxisGameRuntime / WorldState，仅使用纯数学生成器。
 */
public class MenuGalaxyBackground {

    private final WorldCamera camera;
    private final StarBatchRenderer batchRenderer;
    private final StarHaloRenderer haloRenderer;
    private final SkyboxRenderer skyboxRenderer;

    private final float rotationSpeed; // 度/秒

    public MenuGalaxyBackground() {
        this.rotationSpeed = MenuBackgroundConfig.CAMERA_ROTATION_SPEED;

        // 1. 生成星系坐标（纯数学，毫秒级完成）
        GalaxyConfig config = GalaxyConfig.defaultSpiral();
        config.starCount = MenuBackgroundConfig.STAR_COUNT;
        config.worldSeed = MenuBackgroundConfig.WORLD_SEED;
        config.galaxyType = MenuBackgroundConfig.GALAXY_TYPE;
        config.spiralArms = MenuBackgroundConfig.SPIRAL_ARMS;
        config.pitchAngle = MenuBackgroundConfig.PITCH_ANGLE;
        config.armWidth = MenuBackgroundConfig.ARM_WIDTH;
        config.bulgeRatio = MenuBackgroundConfig.BULGE_RATIO;
        GalaxyGenerator generator = GalaxyGeneratorFactory.create(MenuBackgroundConfig.GALAXY_TYPE);
        GalaxyData galaxyData = generator.generate(config);

        // 2. 转为 EntitySnapshot 列表（StarBatchRenderer 的 rebuild 入口）
        List<EntitySnapshot> starSnapshots = buildStarSnapshots(galaxyData);

        // 3. 初始化渲染器
        batchRenderer = new StarBatchRenderer();
        haloRenderer = new StarHaloRenderer();
        skyboxRenderer = new SkyboxRenderer();

        batchRenderer.rebuild(starSnapshots);
        haloRenderer.rebuild(starSnapshots);

        // 4. 初始化镜头
        camera = new WorldCamera(
                MenuBackgroundConfig.CAMERA_NEAR,
                MenuBackgroundConfig.CAMERA_FAR,
                MenuBackgroundConfig.CAMERA_TARGET_LIMIT);
        camera.setMaxOrbitDist(MenuBackgroundConfig.CAMERA_MAX_ORBIT_DIST);
        camera.setZoom(MenuBackgroundConfig.CAMERA_ZOOM);
        camera.pitch = MenuBackgroundConfig.CAMERA_PITCH;
        camera.yaw = MenuBackgroundConfig.CAMERA_YAW;
        camera.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    /**
     * 将 GalaxyData 中的 StarPosition 转为 EntitySnapshot 列表。
     * 仅填充渲染必需的字段，其余设为默认值。
     */
    private static List<EntitySnapshot> buildStarSnapshots(GalaxyData galaxyData) {
        List<EntitySnapshot> list = new ArrayList<>(galaxyData.stars.size());
        for (StarPosition sp : galaxyData.stars) {
            SpacePosition pos = new SpacePosition(sp.galaxyX(), sp.galaxyY(), sp.galaxyZ());
            int temperatureK = spectralTypeToTemperature(sp.spectralType());
            list.add(new EntitySnapshot(
                    sp.starId(),
                    EntityType.STAR,
                    0L,              // systemId（渲染用不到）
                    0L,              // parentEntityId
                    pos,
                    null,            // ownerNationId
                    null,            // ownerPlayerId
                    true,            // isPublic
                    new StarDetails(
                            sp.spectralType().name(),
                            sp.radiusGU(),
                            0.0,      // massSolar（渲染用不到）
                            temperatureK,
                            "",       // description
                            "",       // surfaceTexturePath
                            0.0, 0.0, 0.0,  // systemPos（菜单背景无系统空间）
                            0L           // orbitCenterEntityId
                    )
            ));
        }
        return list;
    }

    /** 光谱类型 → 典型色温（K），用于 TemperatureColor 映射 RGB。 */
    private static int spectralTypeToTemperature(SpectralType type) {
        return switch (type) {
            case O -> 40000;
            case B -> 20000;
            case A -> 8750;
            case F -> 6750;
            case G -> 5600;
            case K -> 4450;
            case M -> 3050;
        };
    }

    /** 每帧调用，驱动镜头自转。 */
    public void update(float dt) {
        camera.yaw += rotationSpeed * dt;
        camera.update(dt);
    }

    /** 渲染银河背景 + 天空盒。 */
    public void render() {
        Gdx.gl.glClearColor(0.005f, 0.005f, 0.02f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        skyboxRenderer.render(camera);
        batchRenderer.render(camera, -1L); // -1 = 无 hover
        haloRenderer.render(camera);
    }

    public void resize(int width, int height) {
        camera.resize(width, height);
    }

    public void dispose() {
        batchRenderer.dispose();
        haloRenderer.dispose();
        skyboxRenderer.dispose();
    }
}
