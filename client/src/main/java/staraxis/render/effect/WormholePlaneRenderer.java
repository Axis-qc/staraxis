package staraxis.render.effect;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;

import staraxis.render.WorldCamera;

/**
 * WormholePlaneRenderer（虫洞平面渲染器）喵。
 *
 * 在初始舰队刷出位置显示一个面向镜头的 billboard 四边形，
 * 表示虫洞出口。使用简单纯色着色器，3 秒渐隐消失。
 *
 * 生命周期由 SystemViewRenderer 管理：
 * - show(position) → 激活并重置 alpha
 * - update(dt) → 渐隐更新
 * - render(camera) → 每帧渲染
 */
public class WormholePlaneRenderer {

    /** 虫洞平面世界半径（GU）。 */
    private static final float WORMHOLE_RADIUS = 1000f;

    /** 渐隐持续时间（秒）。 */
    private static final float FADE_DURATION = 3.0f;

    /** 虫洞颜色：蓝紫色。 */
    private static final float R = 0.3f;
    private static final float G = 0.2f;
    private static final float B = 0.9f;

    // ── 着色器 ──
    private static final String VERT_SHADER = ""
            + "#version 120\n"
            + "attribute vec2 a_corner;\n"
            + "uniform mat4 u_projViewTrans;\n"
            + "uniform vec3 u_position;\n"
            + "uniform vec3 u_camRight;\n"
            + "uniform vec3 u_camUp;\n"
            + "uniform float u_radius;\n"
            + "varying vec2 v_uv;\n"
            + "void main() {\n"
            + "  vec3 worldPos = u_position + (a_corner.x * u_camRight + a_corner.y * u_camUp) * u_radius;\n"
            + "  gl_Position = u_projViewTrans * vec4(worldPos, 1.0);\n"
            + "  v_uv = a_corner * 0.5 + 0.5;\n" // 映射 [-1,1] → [0,1]
            + "}";

    private static final String FRAG_SHADER = ""
            + "#version 120\n"
            + "uniform vec4 u_color;\n"
            + "varying vec2 v_uv;\n"
            + "void main() {\n"
            + "  float d = length(v_uv - 0.5) * 2.0;\n" // 圆心距 0~1
            + "  float alpha = u_color.a * (1.0 - d);\n" // 径向渐变
            + "  gl_FragColor = vec4(u_color.rgb, alpha);\n"
            + "}";

    private Mesh quadMesh;
    private ShaderProgram shader;

    /** 虫洞世界坐标。 */
    private final Vector3 position = new Vector3();

    /** 当前透明度（0 = 完全透明，1 = 完全不透明）。 */
    private float alpha = 0f;

    /** 是否正在显示。 */
    private boolean visible = false;

    public WormholePlaneRenderer() {
        initQuadMesh();
        initShader();
    }

    private void initQuadMesh() {
        float[] verts = new float[] {
                -1f, -1f, -1f, -1f,
                1f, -1f, 1f, -1f,
                1f, 1f, 1f, 1f,
                -1f, 1f, -1f, 1f
        };
        short[] idx = new short[] { 0, 1, 2, 0, 2, 3 };
        quadMesh = new Mesh(true, 4, 6,
                new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
                new VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_corner"));
        quadMesh.setVertices(verts);
        quadMesh.setIndices(idx);
    }

    private void initShader() {
        shader = new ShaderProgram(VERT_SHADER, FRAG_SHADER);
        if (!shader.isCompiled()) {
            throw new RuntimeException("WormholePlane shader compile failed: " + shader.getLog());
        }
    }

    /**
     * 在指定世界坐标激活虫洞平面喵。
     */
    public void show(double x, double y, double z) {
        position.set((float) x, (float) y, (float) z);
        alpha = 1.0f;
        visible = true;
    }

    /**
     * 更新渐隐逻辑喵。
     *
     * @param dt 帧间隔（秒）
     */
    public void update(float dt) {
        if (!visible)
            return;
        alpha -= dt / FADE_DURATION;
        if (alpha <= 0f) {
            alpha = 0f;
            visible = false;
        }
    }

    /**
     * 渲染虫洞平面（billboard）喵。
     */
    public void render(WorldCamera worldCamera) {
        if (!visible || alpha <= 0f)
            return;

        var cam = worldCamera.camera;

        // right = direction x up
        float rx = cam.direction.y * cam.up.z - cam.direction.z * cam.up.y;
        float ry = cam.direction.z * cam.up.x - cam.direction.x * cam.up.z;
        float rz = cam.direction.x * cam.up.y - cam.direction.y * cam.up.x;
        float rLen = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rLen > 1e-6f) {
            rx /= rLen;
            ry /= rLen;
            rz /= rLen;
        }
        float ux = cam.up.x, uy = cam.up.y, uz = cam.up.z;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE); // additive 混合
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", cam.combined);
        shader.setUniformf("u_position", position.x, position.y, position.z);
        shader.setUniformf("u_camRight", rx, ry, rz);
        shader.setUniformf("u_camUp", ux, uy, uz);
        shader.setUniformf("u_radius", WORMHOLE_RADIUS);
        shader.setUniformf("u_color", R, G, B, alpha);

        quadMesh.render(shader, GL20.GL_TRIANGLES);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    public boolean isVisible() {
        return visible;
    }

    public void dispose() {
        if (quadMesh != null)
            quadMesh.dispose();
        if (shader != null)
            shader.dispose();
    }
}
