package staraxis.render.galaxy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;

/**
 * StarBatchRenderer（恒星批量渲染器）。
 *
 * 使用单次 draw call 渲染数万恒星光点。
 * 顶点格式：位置 (x,y,z) + 颜色 (r,g,b,a)
 *
 * 性能优化：
 * - 预分配足够大的顶点缓冲区
 * - 一次 begin/end 之间批量提交所有恒星
 */
public class StarBatchRenderer {

    /** 每个顶点的浮点数数量（x,y,z + r,g,b,a = 7）。 */
    private static final int FLOATS_PER_VERTEX = 7;

    /** 最大恒星数量（可容纳 10 万颗）。 */
    private static final int MAX_STARS = 100_000;

    /** 顶点缓冲区。 */
    private final float[] vertices;

    /** 当前恒星计数。 */
    private int starCount = 0;

    /** OpenGL 网格对象。 */
    private Mesh mesh;

    /** 着色器。 */
    private ShaderProgram shader;

    private boolean begun = false;

    public StarBatchRenderer() {
        vertices = new float[MAX_STARS * FLOATS_PER_VERTEX];
        createMesh();
        createShader();
    }

    private void createMesh() {
        mesh = new Mesh(
            true,
            MAX_STARS,
            0,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
            new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color")
        );
    }

    private void createShader() {
        String vertexShader = """
            attribute vec3 a_position;
            attribute vec4 a_color;
            uniform mat4 u_projViewTrans;
            varying vec4 v_color;
            void main() {
                v_color = a_color;
                gl_Position = u_projViewTrans * vec4(a_position, 1.0);
                gl_PointSize = 3.0;
            }
            """;

        String fragmentShader = """
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec4 v_color;
            void main() {
                gl_FragColor = v_color;
            }
            """;

        shader = new ShaderProgram(vertexShader, fragmentShader);
        if (!shader.isCompiled()) {
            System.err.println("StarBatchRenderer shader compilation failed: " + shader.getLog());
        }
    }

    /**
     * 开始批量渲染。
     *
     * @param projectionView 投影视图矩阵
     */
    public void begin(Matrix4 projectionView) {
        if (begun) {
            throw new IllegalStateException("Already begun");
        }
        begun = true;
        starCount = 0;
    }

    /**
     * 绘制一颗恒星。
     *
     * @param x X 坐标 (GU)
     * @param y Y 坐标 (GU)
     * @param z Z 坐标 (GU)
     * @param r 红色分量 (0-1)
     * @param g 绿色分量 (0-1)
     * @param b 蓝色分量 (0-1)
     * @param hovered 是否悬停（高亮）
     */
    public void drawStar(float x, float y, float z, float r, float g, float b, boolean hovered) {
        if (!begun) {
            throw new IllegalStateException("Must call begin() first");
        }
        if (starCount >= MAX_STARS) {
            return;
        }

        int offset = starCount * FLOATS_PER_VERTEX;
        vertices[offset] = x;
        vertices[offset + 1] = y;
        vertices[offset + 2] = z;

        // 悬停时高亮
        if (hovered) {
            vertices[offset + 3] = 1.0f;
            vertices[offset + 4] = 1.0f;
            vertices[offset + 5] = 1.0f;
        } else {
            vertices[offset + 3] = r;
            vertices[offset + 4] = g;
            vertices[offset + 5] = b;
        }
        vertices[offset + 6] = 1.0f;

        starCount++;
    }

    /**
     * 结束批量渲染并提交到 GPU。
     */
    public void end() {
        if (!begun) {
            throw new IllegalStateException("Must call begin() first");
        }
        begun = false;

        if (starCount == 0) {
            return;
        }

        // 更新网格顶点数据
        mesh.setVertices(vertices, 0, starCount * FLOATS_PER_VERTEX);

        // 渲染
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        shader.bind();
        mesh.render(shader, GL20.GL_POINTS);

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    /**
     * 获取当前批量的恒星数量。
     */
    public int getStarCount() {
        return starCount;
    }

    public void dispose() {
        if (mesh != null) {
            mesh.dispose();
        }
        if (shader != null) {
            shader.dispose();
        }
    }
}
