/*
 * ShipMesh
 *
 * 文件作用：
 * - 舰船可视化的立方体网格，10GU 边长。
 * - 使用 libGDX ModelBuilder 创建立方体。
 *
 * 使用方式：
 * - 由 SystemViewRenderer 创建 ModelInstance 进行渲染。
 *
 * 注意事项：
 * - 单例，全局共享一个 Model。
 * - 颜色在渲染时通过 ModelInstance 的 material 单独设置。
 */

package staraxis.render.mesh;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;

/**
 * ShipMesh（舰船立方体网格）。
 *
 * 10GU 边长的正方体，用于测试阶段可视化舰船位置。
 */
public class ShipMesh {

    /** 正方体边长一半，用于 transform scl。 */
    private static final float HALF_EXTENT = 5f;

    private final Model model;

    public ShipMesh() {
        ModelBuilder builder = new ModelBuilder();
        model = builder.createBox(2f, 2f, 2f,
            new Material(ColorAttribute.createDiffuse(Color.WHITE)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    }

    /**
     * 获取正方体模型（渲染时通过 ModelInstance.transform.scl(HALF_EXTENT) 缩放为 10GU）。
     */
    public Model getModel() {
        return model;
    }

    public void dispose() {
        model.dispose();
    }
}
