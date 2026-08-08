package staraxis.render.adapter;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.utils.Array;

import staraxis.game_asset.data.MaterialData;
import staraxis.game_asset.data.MeshData;
import staraxis.game_asset.data.VertexAttributeType;
import staraxis.game_asset.data.VertexLayout;

/**
 * MeshDataToModel（中性网格数据 -> libGDX Model 转换器）。
 *
 * 将 game_asset 模块产出的中性 {@link MeshData} 转换为 libGDX {@link Model}，
 * 供 libGDX 渲染管线（ModelBatch/ModelInstance）使用。
 *
 * 顶点属性映射：
 * <pre>
 * MeshData 属性          -> libGDX VertexAttributes.Usage
 * POSITION               -> Position
 * NORMAL                 -> Normal
 * TEXCOORD_0             -> TextureCoordinates
 * JOINTS_0/WEIGHTS_0     -> BoneWeight（T2.12 骨骼动画时实现）
 * </pre>
 *
 * 未来 Vulkan 迁移时，新建 MeshDataToVkBuffer 转换器替代此类即可。
 */
public final class MeshDataToModel {

    private MeshDataToModel() {
    }

    /**
     * 将 MeshData + Material 转换为 libGDX Model。
     *
     * @param meshData 中性网格数据（来自 GltfLoader 或程序化生成器）
     * @param material libGDX 材质（由调用方创建，含纹理/颜色/自定义属性等）
     * @return 可直接用于 ModelInstance 渲染的 libGDX Model
     */
    public static Model convert(MeshData meshData, Material material) {
        VertexLayout layout = meshData.layout();

        // 1. 创建顶点属性
        VertexAttributes va = createVertexAttributes(layout);

        // 2. 创建 Mesh 并填充数据
        int vertexCount = meshData.vertexCount();
        int indexCount = meshData.indexCount();
        Mesh mesh = new Mesh(true, vertexCount, indexCount, va);
        mesh.setVertices(meshData.vertices());
        setIndices(mesh, meshData.indices(), vertexCount);

        // 3. 创建 MeshPart
        MeshPart meshPart = new MeshPart();
        meshPart.id = "part1";
        meshPart.primitiveType = GL20.GL_TRIANGLES;
        meshPart.mesh = mesh;
        meshPart.offset = 0;
        meshPart.size = indexCount;

        // 4. 组装 Node + NodePart
        NodePart nodePart = new NodePart();
        nodePart.meshPart = meshPart;
        nodePart.material = material;

        Node node = new Node();
        node.id = "node1";
        Array<NodePart> parts = new Array<>();
        parts.add(nodePart);
        node.parts = parts;

        // 5. 组装 Model
        Model model = new Model();
        model.meshes.add(mesh);
        model.materials.add(material);
        model.meshParts.add(meshPart);
        model.nodes.add(node);
        model.calculateTransforms();

        return model;
    }

    /**
     * 将 MaterialData 转换为 libGDX Material。
     *
     * 加载贴图并挂载对应 TextureAttribute（diffuse/normal/specular/emissive）。
     * 贴图路径为相对 assets 根目录的路径，使用 Gdx.files.internal 加载。
     *
     * @param materialData 中性材质数据（来自 GltfLoader）
     * @return libGDX Material
     */
    public static Material convertMaterial(MaterialData materialData) {
        Material material = new Material();

        if (materialData == null) {
            return material;
        }

        // 基础颜色
        float[] bcf = materialData.baseColorFactor;
        material.set(ColorAttribute.createDiffuse(bcf[0], bcf[1], bcf[2], bcf[3]));
        // 注意：不设置 emissiveFactor 颜色。Stellaris 导出模型的 emissiveFactor 为全白
        // （配合 emissiveTexture 复用 normal 贴图实现发光掩码），设置后会导致模型泛白。
        // 发光由 ShipShader 的 playerColorFlag 分支（normal 蓝通道掩码）处理。

        // 贴图挂载
        if (materialData.baseColorTexturePath != null) {
            Texture tex = new Texture(Gdx.files.internal(materialData.baseColorTexturePath));
            material.set(TextureAttribute.createDiffuse(tex));
        }
        if (materialData.normalTexturePath != null) {
            Texture tex = new Texture(Gdx.files.internal(materialData.normalTexturePath));
            material.set(TextureAttribute.createNormal(tex));
        }
        if (materialData.specularTexturePath != null) {
            Texture tex = new Texture(Gdx.files.internal(materialData.specularTexturePath));
            material.set(TextureAttribute.createSpecular(tex));
        }
        // 注意：不挂 emissive 贴图。Stellaris 导出模型的 emissiveTexture 复用了 normal 贴图
        // （normal 蓝通道兼职发光掩码，emissiveFactor 全白 + 高 emissiveStrength），
        // libGDX shader 会把整张 normal 贴图作为自发光叠加，盖住 diffuse 导致模型
        // 表面显示 normal 贴图颜色。StarAxis 的 ShipShader 已用 normal 蓝通道实现
        // 玩家颜色发光（playerColorFlag 分支），无需再挂 emissive。

        return material;
    }

    /**
     * 将 VertexLayout 映射为 libGDX VertexAttributes。
     *
     * @param layout 中性顶点布局
     * @return libGDX 顶点属性数组
     */
    private static VertexAttributes createVertexAttributes(VertexLayout layout) {
        VertexAttribute[] attrs = new VertexAttribute[layout.attributes().size()];
        for (int i = 0; i < layout.attributes().size(); i++) {
            VertexAttributeType type = layout.attributes().get(i);
            attrs[i] = switch (type) {
                case POSITION -> new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position");
                case NORMAL -> new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal");
                case TEXCOORD_0 -> new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0");
                case TANGENT -> new VertexAttribute(VertexAttributes.Usage.Tangent, 4, "a_tangent");
                case JOINTS_0, WEIGHTS_0 ->
                        throw new UnsupportedOperationException("骨骼动画属性尚未实现，见 T2.12");
            };
        }
        return new VertexAttributes(attrs);
    }

    /**
     * 设置索引数据。libGDX Mesh 只支持 short 索引（顶点数 <= 65535）。
     *
     * @param mesh        libGDX Mesh
     * @param indices     索引数据（int 数组）
     * @param vertexCount 顶点数（决定索引类型）
     */
    private static void setIndices(Mesh mesh, int[] indices, int vertexCount) {
        if (vertexCount > 65535) {
            throw new UnsupportedOperationException(
                    "顶点数 " + vertexCount + " 超过 65535，libGDX Mesh 不支持 int 索引");
        }
        short[] shortIndices = new short[indices.length];
        for (int i = 0; i < indices.length; i++) {
            shortIndices[i] = (short) indices[i];
        }
        mesh.setIndices(shortIndices);
    }
}
