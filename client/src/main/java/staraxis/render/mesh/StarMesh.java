package staraxis.render.mesh;

import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;

/**
 * StarMesh（恒星网格）。
 *
 * 发光恒星球体，用于 System View 渲染。
 * 可配合 billboard 辉光效果（后续实现）。
 */
public class StarMesh {

    /** 恒星球体模型。 */
    private final Model model;

    public StarMesh() {
        ModelBuilder builder = new ModelBuilder();

        // 恒星球体：24x18 段
        model = builder.createSphere(1f, 1f, 1f, 24, 18,
            new Material(),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    }

    /**
     * 获取恒星模型。
     */
    public Model getModel() {
        return model;
    }

    public void dispose() {
        model.dispose();
    }
}
