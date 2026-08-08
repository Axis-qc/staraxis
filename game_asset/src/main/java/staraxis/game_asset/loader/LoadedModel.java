package staraxis.game_asset.loader;

import staraxis.game_asset.data.MaterialData;
import staraxis.game_asset.data.MeshData;

/**
 * LoadedModel（glTF 文件加载结果）。
 *
 * 包含解析后的中性网格数据和材质数据，
 * 由 client 层的 {@code MeshDataToModel} 转换为可渲染的 Model。
 */
public class LoadedModel {

    /** 网格数据 */
    public final MeshData mesh;

    /** 材质数据 */
    public final MaterialData material;

    public LoadedModel(MeshData mesh, MaterialData material) {
        this.mesh = mesh;
        this.material = material;
    }
}
