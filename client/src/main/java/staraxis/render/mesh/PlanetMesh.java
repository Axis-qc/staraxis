package staraxis.render.mesh;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;

/**
 * PlanetMesh（行星球体网格）。
 *
 * 低 poly 球体（16-32 段），按 radiusGU 缩放渲染。
 * 颜色由 PlanetData 决定。
 */
public class PlanetMesh {

    /** 高精度球体（FULL LOD）。 */
    private final Model highDetail;

    /** 低精度球体（LOW LOD）。 */
    private final Model lowDetail;

    public PlanetMesh() {
        ModelBuilder builder = new ModelBuilder();

        // 高精度：32x24 段，直径 2（半径 1），scl(radiusGU) 后半径 = radiusGU
        highDetail = builder.createSphere(2f, 2f, 2f, 32, 24,
            new Material(ColorAttribute.createDiffuse(Color.WHITE)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        // 低精度：12x8 段
        lowDetail = builder.createSphere(2f, 2f, 2f, 12, 8,
            new Material(ColorAttribute.createDiffuse(Color.WHITE)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    }

    /**
     * 获取高精度球体模型。
     */
    public Model getHighDetail() {
        return highDetail;
    }

    /**
     * 获取低精度球体模型。
     */
    public Model getLowDetail() {
        return lowDetail;
    }

    public void dispose() {
        highDetail.dispose();
        lowDetail.dispose();
    }
}
