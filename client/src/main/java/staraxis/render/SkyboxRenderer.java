package staraxis.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Cubemap;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.GdxRuntimeException;

/**
 * SkyboxRenderer（天空盒渲染器）
 *
 * 使用 cubemap 纹理绘制宇宙背景天空盒。
 * 利用 gl_Position.xyww 将立方体推到远平面，使天空盒始终处于场景最远处。
 * 观察矩阵的平移分量被移除，以确保天空盒随相机旋转但不会位移。
 */
public class SkyboxRenderer {

    private static final String VERT_SHADER = ""
            + "attribute vec3 a_position;\n"
            + "uniform mat4 u_projViewTrans;\n"
            + "varying vec3 v_texCoord;\n"
            + "void main() {\n"
            + "  vec4 pos = u_projViewTrans * vec4(a_position, 1.0);\n"
            + "  gl_Position = pos.xyww;\n"
            + "  v_texCoord = a_position;\n"
            + "}\n";

    private static final String FRAG_SHADER = ""
            + "varying vec3 v_texCoord;\n"
            + "uniform samplerCube u_cubemap;\n"
            + "void main() {\n"
            + "  gl_FragColor = textureCube(u_cubemap, v_texCoord);\n"
            + "}\n";

    private Mesh cubeMesh;
    private ShaderProgram shader;
    private Cubemap cubemap;

    private int uProjViewTrans;
    private int uCubemap;

    /** 缓存矩阵：零平移视图矩阵 */
    private final Matrix4 cleanView = new Matrix4();
    /** 缓存矩阵：投影 * 零平移视图 */
    private final Matrix4 skyProjView = new Matrix4();

    public SkyboxRenderer() {
        initShader();
        initMesh();
        initCubemap();
    }

    private void initShader() {
        shader = new ShaderProgram(VERT_SHADER, FRAG_SHADER);
        if (!shader.isCompiled()) {
            throw new GdxRuntimeException("Skybox shader compilation failed: " + shader.getLog());
        }
        uProjViewTrans = shader.getUniformLocation("u_projViewTrans");
        uCubemap = shader.getUniformLocation("u_cubemap");
    }

    /** 创建单位立方体网格，顶点位置同时作为 cubemap 采样方向。 */
    private void initMesh() {
        float[] verts = {
            // 背面 (Z-)
            -1, -1, -1,   1, -1, -1,   1,  1, -1,  -1,  1, -1,
            // 正面 (Z+)
            -1, -1,  1,   1, -1,  1,   1,  1,  1,  -1,  1,  1,
            // 左面 (X-)
            -1, -1, -1,  -1,  1, -1,  -1,  1,  1,  -1, -1,  1,
            // 右面 (X+)
             1, -1, -1,   1,  1, -1,   1,  1,  1,   1, -1,  1,
            // 顶面 (Y+)
            -1,  1, -1,   1,  1, -1,   1,  1,  1,  -1,  1,  1,
            // 底面 (Y-)
            -1, -1, -1,   1, -1, -1,   1, -1,  1,  -1, -1,  1
        };
        short[] idx = {
             0,  1,  2,   2,  3,  0,
             4,  5,  6,   6,  7,  4,
             8,  9, 10,  10, 11,  8,
            12, 13, 14,  14, 15, 12,
            16, 17, 18,  18, 19, 16,
            20, 21, 22,  22, 23, 20
        };

        cubeMesh = new Mesh(true, 24, 36,
                new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"));
        cubeMesh.setVertices(verts);
        cubeMesh.setIndices(idx);
    }

    /** 从 6 张纹理加载 cubemap。 */
    private void initCubemap() {
        cubemap = new Cubemap(
                Gdx.files.internal("world/sky_pos_x.jpg"),
                Gdx.files.internal("world/sky_neg_x.jpg"),
                Gdx.files.internal("world/sky_pos_y.jpg"),
                Gdx.files.internal("world/sky_neg_y.jpg"),
                Gdx.files.internal("world/sky_pos_z.jpg"),
                Gdx.files.internal("world/sky_neg_z.jpg"));
        cubemap.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        cubemap.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
    }

    /**
     * 渲染天空盒。
     *
     * 使用 GL_LEQUAL + 深度掩码关闭 确保天空盒始终在场景物体之后。
     * 调用后恢复深度状态，不影响后续渲染。
     */
    public void render(WorldCamera camera) {
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(false);

        // 复制视图矩阵并清零平移分量，使天空盒固定在无穷远处
        cleanView.set(camera.camera.view);
        cleanView.val[Matrix4.M03] = 0;
        cleanView.val[Matrix4.M13] = 0;
        cleanView.val[Matrix4.M23] = 0;

        // skyProjView = projection * cleanView
        skyProjView.set(camera.camera.projection);
        skyProjView.mul(cleanView);

        shader.bind();
        shader.setUniformMatrix(uProjViewTrans, skyProjView);
        shader.setUniformi(uCubemap, 0);

        cubemap.bind(0);
        cubeMesh.render(shader, GL20.GL_TRIANGLES);

        // 恢复深度状态
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDepthFunc(GL20.GL_LESS);
    }

    public void dispose() {
        if (cubeMesh != null) {
            cubeMesh.dispose();
            cubeMesh = null;
        }
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
        if (cubemap != null) {
            cubemap.dispose();
            cubemap = null;
        }
    }
}
