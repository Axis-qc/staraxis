package staraxis.game_asset.generator;

import staraxis.game_asset.data.MeshData;
import staraxis.game_asset.data.VertexAttributeType;
import staraxis.game_asset.data.VertexLayout;

/**
 * 立方体网格生成器（程序化生成，不依赖任何渲染 API）。
 *
 * 生成 6 个面的立方体，每个面 4 个顶点（因为不同面法线不同）+ 2 个三角形。
 * 顶点布局：POSITION + NORMAL。
 *
 * 替代 libGDX {@code ModelBuilder.createBox}，算法独立，跨渲染后端复用。
 */
public final class BoxGenerator {

    private BoxGenerator() {
    }

    /**
     * 生成立方体网格。
     *
     * @param size 立方体边长
     * @return 立方体网格数据
     */
    public static MeshData generate(float size) {
        VertexLayout layout = new VertexLayout(VertexAttributeType.POSITION, VertexAttributeType.NORMAL);
        int stride = layout.stride();

        float half = size / 2f;

        // 6 个面，每面 4 个顶点，每顶点 6 个 float（position 3 + normal 3）
        float[] vertices = new float[6 * 4 * stride];
        int[] indices = new int[6 * 6]; // 6 个面，每面 2 个三角形，每三角形 3 个索引

        int vi = 0;
        int ii = 0;

        // 每个面的顶点定义：4 个顶点（左下、右下、左上、右上），法线朝外
        // 面定义顺序：+X, -X, +Y, -Y, +Z, -Z

        // +X 面（右）
        float[] nx = {1, 0, 0};
        vi = writeFace(vertices, vi, indices, ii,
                half, -half, -half,
                half, half, -half,
                half, -half, half,
                half, half, half,
                nx);
        ii += 6;

        // -X 面（左）
        float[] nmx = {-1, 0, 0};
        vi = writeFace(vertices, vi, indices, ii,
                -half, -half, half,
                -half, half, half,
                -half, -half, -half,
                -half, half, -half,
                nmx);
        ii += 6;

        // +Y 面（上）
        float[] ny = {0, 1, 0};
        vi = writeFace(vertices, vi, indices, ii,
                -half, half, half,
                half, half, half,
                -half, half, -half,
                half, half, -half,
                ny);
        ii += 6;

        // -Y 面（下）
        float[] nmy = {0, -1, 0};
        vi = writeFace(vertices, vi, indices, ii,
                -half, -half, -half,
                half, -half, -half,
                -half, -half, half,
                half, -half, half,
                nmy);
        ii += 6;

        // +Z 面（前）
        float[] nz = {0, 0, 1};
        vi = writeFace(vertices, vi, indices, ii,
                -half, -half, half,
                half, -half, half,
                -half, half, half,
                half, half, half,
                nz);
        ii += 6;

        // -Z 面（后）
        float[] nmz = {0, 0, -1};
        writeFace(vertices, vi, indices, ii,
                half, -half, -half,
                -half, -half, -half,
                half, half, -half,
                -half, half, -half,
                nmz);

        return new MeshData(layout, vertices, indices);
    }

    /**
     * 写入一个面的 4 个顶点 + 2 个三角形索引。
     *
     * @param vertices 顶点数组
     * @param vOffset   顶点写入偏移
     * @param indices   索引数组
     * @param iOffset   索引写入偏移
     * @param x1 y1 z1  左下顶点位置
     * @param x2 y2 z2  右下顶点位置
     * @param x3 y3 z3  左上顶点位置
     * @param x4 y4 z4  右上顶点位置
     * @param normal    面法线（4 个顶点共用）
     * @return 写入后的顶点偏移
     */
    private static int writeFace(float[] vertices, int vOffset, int[] indices, int iOffset,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3,
                                 float x4, float y4, float z4,
                                 float[] normal) {
        int stride = 6; // POSITION(3) + NORMAL(3)

        // 4 个顶点位置
        vertices[vOffset++] = x1; vertices[vOffset++] = y1; vertices[vOffset++] = z1;
        vertices[vOffset++] = normal[0]; vertices[vOffset++] = normal[1]; vertices[vOffset++] = normal[2];

        vertices[vOffset++] = x2; vertices[vOffset++] = y2; vertices[vOffset++] = z2;
        vertices[vOffset++] = normal[0]; vertices[vOffset++] = normal[1]; vertices[vOffset++] = normal[2];

        vertices[vOffset++] = x3; vertices[vOffset++] = y3; vertices[vOffset++] = z3;
        vertices[vOffset++] = normal[0]; vertices[vOffset++] = normal[1]; vertices[vOffset++] = normal[2];

        vertices[vOffset++] = x4; vertices[vOffset++] = y4; vertices[vOffset++] = z4;
        vertices[vOffset++] = normal[0]; vertices[vOffset++] = normal[1]; vertices[vOffset++] = normal[2];

        // 2 个三角形：左下 -> 右下 -> 左上，右下 -> 右上 -> 左上
        int base = iOffset / 6 * 4; // 当前面的第一个顶点索引
        indices[iOffset++] = base;
        indices[iOffset++] = base + 1;
        indices[iOffset++] = base + 2;
        indices[iOffset++] = base + 1;
        indices[iOffset++] = base + 3;
        indices[iOffset++] = base + 2;

        return vOffset;
    }
}
