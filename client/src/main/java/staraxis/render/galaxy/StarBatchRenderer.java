package staraxis.render.galaxy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;

import staraxis.game.space.galaxy.GalaxyData;
import staraxis.game.space.galaxy.SpectralType;
import staraxis.game.space.galaxy.StarPosition;
import staraxis.render.WorldCamera;

/**
 * StarBatchRenderer（恒星批量渲染器）。
 *
 * 每种光谱类型一个固定大小的球体模型，按类型分组用 ModelBatch 批量渲染。
 * 恒星大小由 SpectralType 的半径范围决定。
 */
public class StarBatchRenderer {

    /** 恒星球体半径（GU），固定大小。 */
    private static final float STAR_RADIUS = 40f;

    private final ModelBatch modelBatch;
    private final Environment environment;
    private final Model starModel;

    /** 预分配的 ModelInstance 池，按类型分组。 */
    private ModelInstance[] instances;
    private int instanceCount = 0;

    public StarBatchRenderer() {
        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.5f, 0.5f, 0.6f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.9f, 0, 1, 0));

        ModelBuilder builder = new ModelBuilder();
        starModel = builder.createSphere(
            STAR_RADIUS * 2, STAR_RADIUS * 2, STAR_RADIUS * 2,
            8, 6,
            new Material(),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal
        );
    }

    /**
     * 渲染整个星系的所有恒星。
     *
     * @param galaxy 星系数据
     * @param camera 世界相机
     * @param hoveredStarId 悬停的恒星ID（-1 表示无）
     */
    public void render(GalaxyData galaxy, WorldCamera camera, long hoveredStarId) {
        // 确保实例池足够大
        int needed = galaxy.starCount();
        if (instances == null || instances.length < needed) {
            instances = new ModelInstance[needed];
            for (int i = 0; i < needed; i++) {
                instances[i] = new ModelInstance(starModel);
            }
        }
        instanceCount = needed;

        // 设置每个实例的位置和颜色
        for (int i = 0; i < instanceCount; i++) {
            StarPosition star = galaxy.stars.get(i);
            ModelInstance inst = instances[i];

            inst.transform.idt();
            inst.transform.translate(
                (float) star.galaxyX(),
                (float) star.galaxyY(),
                (float) star.galaxyZ()
            );

            SpectralType type = star.spectralType();
            float r, g, b;
            if (star.starId() == hoveredStarId) {
                r = 1f; g = 1f; b = 1f;
            } else {
                r = type.colorR;
                g = type.colorG;
                b = type.colorB;
            }
            inst.materials.get(0).set(ColorAttribute.createDiffuse(r, g, b, 1f));
        }

        // 批量渲染
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        modelBatch.begin(camera.camera);
        for (int i = 0; i < instanceCount; i++) {
            modelBatch.render(instances[i], environment);
        }
        modelBatch.end();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    public void dispose() {
        modelBatch.dispose();
        starModel.dispose();
    }
}
