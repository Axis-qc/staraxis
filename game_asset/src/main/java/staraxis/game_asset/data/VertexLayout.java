package staraxis.game_asset.data;

import java.util.List;

/**
 * 顶点布局描述（中性，不依赖任何渲染 API）。
 *
 * 定义一个顶点包含哪些属性，以及它们的排列顺序。
 * 顶点数据按此布局交错存储在 {@link MeshData} 的 vertices 数组中。
 *
 * 例如 layout = [POSITION(3), NORMAL(3), TEXCOORD_0(2)]，stride = 8，
 * 顶点 0 的数据在 vertices[0..7]，顶点 1 在 vertices[8..15]，以此类推。
 */
public class VertexLayout {

    /** 属性列表（定义排列顺序） */
    private final List<VertexAttributeType> attributes;

    /** 每个顶点占用的 float 数量（所有属性分量数之和） */
    private final int stride;

    /**
     * 按给定属性顺序构造顶点布局。
     *
     * @param attributes 属性列表，顺序决定顶点数据中的排列顺序
     */
    public VertexLayout(VertexAttributeType... attributes) {
        this.attributes = List.of(attributes);
        int s = 0;
        for (VertexAttributeType attr : attributes) {
            s += attr.componentCount;
        }
        this.stride = s;
    }

    /** 属性列表（不可变） */
    public List<VertexAttributeType> attributes() {
        return attributes;
    }

    /** 每个顶点占用的 float 数量 */
    public int stride() {
        return stride;
    }

    /** 是否包含指定属性 */
    public boolean has(VertexAttributeType type) {
        return attributes.contains(type);
    }
}
