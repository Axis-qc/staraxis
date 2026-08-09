package staraxis.render.mesh;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;

import staraxis.game_asset.data.MeshData;
import staraxis.game_asset.generator.SphereGenerator;
import staraxis.render.adapter.MeshDataToModel;
import staraxis.render.shader.PlanetLightAttribute;

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
        // 高精度：32x24 段，直径 2（半径 1），scl(radiusGU) 后半径 = radiusGU
        MeshData highData = SphereGenerator.generate(2f, 32, 24);
        Material highMat = new Material(ColorAttribute.createDiffuse(Color.WHITE));
        highMat.set(new PlanetLightAttribute());
        highDetail = MeshDataToModel.convert(highData, highMat);

        // 低精度：12x8 段
        MeshData lowData = SphereGenerator.generate(2f, 12, 8);
        Material lowMat = new Material(ColorAttribute.createDiffuse(Color.WHITE));
        lowMat.set(new PlanetLightAttribute());
        lowDetail = MeshDataToModel.convert(lowData, lowMat);
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
