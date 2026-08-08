package staraxis.game_asset.data;

/**
 * 顶点属性类型（中性描述，不依赖任何渲染 API）。
 *
 * 用于 {@link VertexLayout} 的顶点布局描述，
 * {@code MeshDataToModel} 转换器据此映射到具体渲染后端的顶点属性。
 *
 * 组件数说明：
 * - POSITION/NORMAL 各 3 个 float（x, y, z）
 * - TEXCOORD_0 为 2 个 float（u, v）
 * - TANGENT 为 4 个 float（xyz = 切线方向，w = bitangent 符号，用于 normal mapping）
 * - JOINTS_0/WEIGHTS_0 各 4 个 float（glTF 原始 JOINTS 为 unsigned byte，解析时转为 float）
 */
public enum VertexAttributeType {

    /** 顶点位置（VEC3，3 个 float） */
    POSITION(3),

    /** 顶点法线（VEC3，3 个 float） */
    NORMAL(3),

    /** 纹理坐标 0（VEC2，2 个 float） */
    TEXCOORD_0(2),

    /** 切线（VEC4，4 个 float：xyz 切线方向 + w bitangent 符号，normal mapping 用） */
    TANGENT(4),

    /** 骨骼关节索引（VEC4，4 个 float，原始为 unsigned byte，解析时转为 float） */
    JOINTS_0(4),

    /** 骨骼权重（VEC4，4 个 float） */
    WEIGHTS_0(4);

    /** 每个属性的 float 分量数 */
    public final int componentCount;

    VertexAttributeType(int componentCount) {
        this.componentCount = componentCount;
    }
}
