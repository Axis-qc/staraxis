package staraxis.game_asset.data;

/**
 * MeshData（中性网格数据，不依赖任何渲染 API）。
 *
 * 顶点数据按 {@link VertexLayout} 交错存储在 vertices 数组中。
 * 索引数据存储在 indices 数组中，每 3 个索引组成一个三角形。
 *
 * 此结构由 {@code GltfLoader}（文件解析）或 {@code SphereGenerator}/{@code BoxGenerator}
 * （程序化生成）产出，由 {@code MeshDataToModel} 转换为具体渲染后端的网格对象。
 */
public class MeshData {

    /** 顶点布局 */
    private final VertexLayout layout;

    /** 顶点数据（交错存储，按 layout 顺序排列） */
    private final float[] vertices;

    /** 索引数据（每 3 个一组组成三角形） */
    private final int[] indices;

    /**
     * 构造网格数据。
     *
     * @param layout   顶点布局
     * @param vertices 顶点数据（交错存储）
     * @param indices  索引数据
     */
    public MeshData(VertexLayout layout, float[] vertices, int[] indices) {
        this.layout = layout;
        this.vertices = vertices;
        this.indices = indices;
    }

    /** 顶点布局 */
    public VertexLayout layout() {
        return layout;
    }

    /** 顶点数据（交错存储） */
    public float[] vertices() {
        return vertices;
    }

    /** 索引数据 */
    public int[] indices() {
        return indices;
    }

    /** 顶点数量 */
    public int vertexCount() {
        return vertices.length / layout.stride();
    }

    /** 索引数量 */
    public int indexCount() {
        return indices.length;
    }
}
