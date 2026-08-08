package staraxis.game_asset.generator;

import staraxis.game_asset.data.MeshData;
import staraxis.game_asset.data.VertexAttributeType;
import staraxis.game_asset.data.VertexLayout;

/**
 * 球体网格生成器（程序化生成，不依赖任何渲染 API）。
 *
 * 使用 UV 参数化方法生成球体顶点，支持指定经纬度分段数。
 * 顶点布局：POSITION + NORMAL（程序化球体不需要纹理坐标）。
 *
 * 替代 libGDX {@code ModelBuilder.createSphere}，算法独立，跨渲染后端复用。
 */
public final class SphereGenerator {

    private SphereGenerator() {
    }

    /**
     * 生成球体网格。
     *
     * @param diameter       球体直径（半径 = diameter / 2）
     * @param widthSegments  经度分段数（沿赤道方向，越大越圆滑）
     * @param heightSegments 纬度分段数（北极到南极，越大越圆滑）
     * @return 球体网格数据
     */
    public static MeshData generate(float diameter, int widthSegments, int heightSegments) {
        VertexLayout layout = new VertexLayout(VertexAttributeType.POSITION, VertexAttributeType.NORMAL);
        int stride = layout.stride();

        float radius = diameter / 2f;
        int vertexCount = (widthSegments + 1) * (heightSegments + 1);
        float[] vertices = new float[vertexCount * stride];

        // 生成顶点：沿纬度从北极到南极，沿经度绕一圈
        int vi = 0;
        for (int y = 0; y <= heightSegments; y++) {
            float v = (float) y / heightSegments;
            float phi = v * (float) Math.PI;
            float sinPhi = (float) Math.sin(phi);
            float cosPhi = (float) Math.cos(phi);

            for (int x = 0; x <= widthSegments; x++) {
                float u = (float) x / widthSegments;
                float theta = u * 2f * (float) Math.PI;
                float sinTheta = (float) Math.sin(theta);
                float cosTheta = (float) Math.cos(theta);

                // 法线方向（单位向量）
                float nx = cosTheta * sinPhi;
                float ny = cosPhi;
                float nz = sinTheta * sinPhi;

                // 位置 = 法线方向 * 半径
                vertices[vi++] = nx * radius;
                vertices[vi++] = ny * radius;
                vertices[vi++] = nz * radius;
                vertices[vi++] = nx;
                vertices[vi++] = ny;
                vertices[vi++] = nz;
            }
        }

        // 生成索引：每个网格四边形拆成 2 个三角形
        int[] indices = new int[widthSegments * heightSegments * 6];
        int ii = 0;
        for (int y = 0; y < heightSegments; y++) {
            for (int x = 0; x < widthSegments; x++) {
                int a = y * (widthSegments + 1) + x;
                int b = a + 1;
                int c = a + (widthSegments + 1);
                int d = c + 1;

                indices[ii++] = a;
                indices[ii++] = c;
                indices[ii++] = b;
                indices[ii++] = b;
                indices[ii++] = c;
                indices[ii++] = d;
            }
        }

        return new MeshData(layout, vertices, indices);
    }
}
