package staraxis.render.galaxy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import static com.badlogic.gdx.graphics.GL20.GL_ARRAY_BUFFER;
import static com.badlogic.gdx.graphics.GL20.GL_FLOAT;
import static com.badlogic.gdx.graphics.GL20.GL_STATIC_DRAW;
import static com.badlogic.gdx.graphics.GL20.GL_TRIANGLES;
import static com.badlogic.gdx.graphics.GL20.GL_UNSIGNED_SHORT;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;

import staraxis.game.entity.EntityType;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.game.state.snapshot.EntitySnapshot.StarDetails;
import staraxis.render.WorldCamera;
import staraxis.render.util.TemperatureColor;

/**
 * StarHaloRenderer（恒星光晕渲染器）— 实例化 billboard + 程序化径向渐变。
 *
 * 在每个恒星球位置叠加一个面向相机的光晕四边形，additive 混合，
 * 亮度随镜头距离补偿提亮（远处亮、近处暗），让星系整体呈现明亮发光感。
 * 恒星球本身的自发光颜色不受影响（由 StarBatchRenderer 负责）。
 */
public class StarHaloRenderer {

    // 光晕四边形世界单位半径（恒星球半径 40f 的 4 倍）
    private static final float HALO_SIZE = 160f;

    // ── 着色器 ──────────────────────────────────────────────────
    //  顶点着色器：把 quad 角点按相机 right/up 展开到世界空间，实现 billboard
    private static final String VERT_SHADER = ""
            + "#version 120\n"
            + "attribute vec2 a_corner;\n"           // quad 角点 (-1,-1)..(1,1)
            + "attribute vec3 i_position;\n"         // 实例位置
            + "attribute vec3 i_color;\n"            // 实例颜色
            + "attribute float i_size;\n"           // 实例光晕半径（世界单位）
            + "uniform mat4 u_projViewTrans;\n"
            + "uniform vec3 u_camRight;\n"
            + "uniform vec3 u_camUp;\n"
            + "uniform vec3 u_cameraPos;\n"
            + "uniform float u_refDist;\n"
            + "uniform float u_minBoost;\n"
            + "uniform float u_maxBoost;\n"
            + "uniform float u_minSizeScale;\n"
            + "uniform float u_maxSizeScale;\n"
            + "varying vec3 v_color;\n"
            + "varying vec2 v_uv;\n"
            + "varying float v_intensity;\n"
            + "void main() {\n"
            + "  float dist = distance(i_position, u_cameraPos);\n"
            //  光晕世界大小随距离放大，补偿透视缩小，远处屏幕上更明显
            + "  float sizeScale = clamp(dist / u_refDist, u_minSizeScale, u_maxSizeScale);\n"
            //  billboard：角点沿相机 right/up 展开，加到实例位置
            + "  vec3 worldPos = i_position + (a_corner.x * u_camRight + a_corner.y * u_camUp) * i_size * sizeScale;\n"
            + "  gl_Position = u_projViewTrans * vec4(worldPos, 1.0);\n"
            + "  v_color = i_color;\n"
            + "  v_uv = a_corner;\n"
            //  距离补偿提亮：远处 boost 大，近处小
            + "  v_intensity = clamp(dist / u_refDist, u_minBoost, u_maxBoost);\n"
            + "}\n";

    //  片段着色器：程序化径向渐变，中心亮边缘透明
    private static final String FRAG_SHADER = ""
            + "#version 120\n"
            + "varying vec3 v_color;\n"
            + "varying vec2 v_uv;\n"
            + "varying float v_intensity;\n"
            + "void main() {\n"
            //  d = 0 中心，1 边缘；smoothstep 让中心亮、边缘平滑过渡到 0
            + "  float d = length(v_uv);\n"
            + "  float alpha = smoothstep(1.0, 0.0, d) * v_intensity;\n"
            + "  gl_FragColor = vec4(v_color * alpha, alpha);\n"
            + "}\n";

    // ── 实例数据布局 ──────────────────────────────────────────────
    //  每实例：pos(3) + color(3) + size(1) = 7 floats
    private static final int FLOATS_PER_INSTANCE = 7;
    private static final int BYTES_PER_INSTANCE = FLOATS_PER_INSTANCE * 4;

    // ── 几何体 ──────────────────────────────────────────────────
    private Mesh quadMesh;

    // ── 实例数据 ──────────────────────────────────────────────────
    private int instanceVBO;
    private int instanceCount;

    // ── 着色器 ──────────────────────────────────────────────────
    private ShaderProgram shader;
    private int uProjViewTrans;
    private int uCamRight;
    private int uCamUp;
    private int uCameraPos;
    private int uRefDist;
    private int uMinBoost;
    private int uMaxBoost;
    private int uMinSizeScale;
    private int uMaxSizeScale;
    private int iPositionLoc;
    private int iColorLoc;
    private int iSizeLoc;

    // ── 距离补偿参数（可运行时调） ──────────────────────────────
    private float refDist = 4000f;   // 满亮参考距离
    private float minBoost = 0.2f;   // 近处亮度下限（光晕弱，避免贴脸过曝）
    private float maxBoost = 1.0f;  // 远处亮度上限（提亮让星系明亮）
    private float minSizeScale = 1.0f; // 近处光晕尺寸下限
    private float maxSizeScale = 2.0f; // 远处光晕尺寸上限（放大补偿透视缩小）

    // ── 临时向量（避免每帧 GC） ──────────────────────────────────
    private final Vector3 tmpRight = new Vector3();
    private final Vector3 tmpUp = new Vector3();

    // ── 状态 ──────────────────────────────────────────────────
    private boolean built;

    public StarHaloRenderer() {
        initShader();
        initQuadMesh();
        initInstanceBuffer();
    }

    // ═══════════════════════════════════════════════════════════
    //  初始化
    // ═══════════════════════════════════════════════════════════

    private void initShader() {
        shader = new ShaderProgram(VERT_SHADER, FRAG_SHADER);
        if (!shader.isCompiled()) {
            throw new GdxRuntimeException("恒星光晕着色器编译失败: " + shader.getLog());
        }
        uProjViewTrans = shader.getUniformLocation("u_projViewTrans");
        uCamRight = shader.getUniformLocation("u_camRight");
        uCamUp = shader.getUniformLocation("u_camUp");
        uCameraPos = shader.getUniformLocation("u_cameraPos");
        uRefDist = shader.getUniformLocation("u_refDist");
        uMinBoost = shader.getUniformLocation("u_minBoost");
        uMaxBoost = shader.getUniformLocation("u_maxBoost");
        uMinSizeScale = shader.getUniformLocation("u_minSizeScale");
        uMaxSizeScale = shader.getUniformLocation("u_maxSizeScale");
        iPositionLoc = shader.getAttributeLocation("i_position");
        iColorLoc = shader.getAttributeLocation("i_color");
        iSizeLoc = shader.getAttributeLocation("i_size");
    }

    /** 单位 quad（4 顶点，2 三角形），corner 属性存角点坐标 */
    private void initQuadMesh() {
        //  顶点：pos2 + corner2
        float[] verts = new float[]{
                -1f, -1f, -1f, -1f,
                1f, -1f, 1f, -1f,
                1f, 1f, 1f, 1f,
                -1f, 1f, -1f, 1f
        };
        short[] idx = new short[]{0, 1, 2, 0, 2, 3};

        quadMesh = new Mesh(true, 4, 6,
                new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
                new VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_corner"));
        quadMesh.setVertices(verts);
        quadMesh.setIndices(idx);
    }

    private void initInstanceBuffer() {
        IntBuffer buf = BufferUtils.newByteBuffer(4).order(ByteOrder.nativeOrder()).asIntBuffer();
        Gdx.gl32.glGenBuffers(1, buf);
        instanceVBO = buf.get(0);
    }

    // ═══════════════════════════════════════════════════════════
    //  公开 API
    // ═══════════════════════════════════════════════════════════

    /** 从世界状态重建实例缓冲区（与 StarBatchRenderer 同源数据） */
    public void rebuild(RealTimeWorldState state) {
        java.util.ArrayList<EntitySnapshot> stars = new java.util.ArrayList<>();
        for (EntitySnapshot snap : state.getEntitySnapshotsView()) {
            if (snap != null && snap.entityType == EntityType.STAR) {
                stars.add(snap);
            }
        }

        int n = stars.size();
        instanceCount = n;

        ByteBuffer bb = BufferUtils.newByteBuffer(n * BYTES_PER_INSTANCE);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();

        for (int i = 0; i < n; i++) {
            EntitySnapshot s = stars.get(i);

            float px = (float) s.posWorldGU.x();
            float py = (float) s.posWorldGU.y();
            float pz = (float) s.posWorldGU.z();
            fb.put(px);
            fb.put(py);
            fb.put(pz);

            float[] rgb;
            if (s.details instanceof StarDetails sd) {
                rgb = TemperatureColor.temperatureToRgb(sd.temperatureK);
            } else {
                rgb = new float[]{1f, 0.92f, 0.6f};
            }
            fb.put(rgb[0]);
            fb.put(rgb[1]);
            fb.put(rgb[2]);

            fb.put(HALO_SIZE);
        }
        fb.flip();

        Gdx.gl32.glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        Gdx.gl32.glBufferData(GL_ARRAY_BUFFER, bb.limit() * 4, bb, GL_STATIC_DRAW);
        Gdx.gl32.glBindBuffer(GL_ARRAY_BUFFER, 0);

        built = true;
    }

    /** 渲染全部恒星光晕。每帧一次 instanced draw call，additive 混合。 */
    public void render(WorldCamera camera) {
        if (!built || instanceCount == 0) return;

        //  billboard 需要相机 right/up 向量：right = direction × up
        tmpRight.set(camera.camera.direction).crs(camera.camera.up).nor();
        tmpUp.set(camera.camera.up).nor();

        //  光晕开启深度测试（被前方恒星球遮挡）、关闭深度写入（不污染深度缓冲）
        //  渲染顺序：光晕在 StarBatchRenderer 之后，恒星球已写入深度，故前方球能遮挡后方光晕
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(false);
        //  additive 混合：颜色相加，产生明亮发光感
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

        shader.bind();
        shader.setUniformMatrix(uProjViewTrans, camera.camera.combined);
        shader.setUniformf(uCamRight, tmpRight);
        shader.setUniformf(uCamUp, tmpUp);
        shader.setUniformf(uCameraPos, camera.camera.position);
        shader.setUniformf(uRefDist, refDist);
        shader.setUniformf(uMinBoost, minBoost);
        shader.setUniformf(uMaxBoost, maxBoost);
        shader.setUniformf(uMinSizeScale, minSizeScale);
        shader.setUniformf(uMaxSizeScale, maxSizeScale);

        quadMesh.bind(shader);

        //  绑定实例属性
        Gdx.gl32.glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        Gdx.gl32.glEnableVertexAttribArray(iPositionLoc);
        Gdx.gl32.glVertexAttribPointer(iPositionLoc, 3, GL_FLOAT, false, BYTES_PER_INSTANCE, 0);
        Gdx.gl32.glVertexAttribDivisor(iPositionLoc, 1);
        Gdx.gl32.glEnableVertexAttribArray(iColorLoc);
        Gdx.gl32.glVertexAttribPointer(iColorLoc, 3, GL_FLOAT, false, BYTES_PER_INSTANCE, 12);
        Gdx.gl32.glVertexAttribDivisor(iColorLoc, 1);
        Gdx.gl32.glEnableVertexAttribArray(iSizeLoc);
        Gdx.gl32.glVertexAttribPointer(iSizeLoc, 1, GL_FLOAT, false, BYTES_PER_INSTANCE, 24);
        Gdx.gl32.glVertexAttribDivisor(iSizeLoc, 1);

        Gdx.gl32.glDrawElementsInstanced(
                GL_TRIANGLES,
                quadMesh.getNumIndices(),
                GL_UNSIGNED_SHORT,
                0,
                instanceCount);

        //  清理
        Gdx.gl32.glDisableVertexAttribArray(iPositionLoc);
        Gdx.gl32.glDisableVertexAttribArray(iColorLoc);
        Gdx.gl32.glDisableVertexAttribArray(iSizeLoc);
        Gdx.gl32.glBindBuffer(GL_ARRAY_BUFFER, 0);
        quadMesh.unbind(shader);

        //  恢复混合/深度状态
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDepthFunc(GL20.GL_LESS);
    }

    public boolean isBuilt() {
        return built;
    }

    // ═══════════════════════════════════════════════════════════
    //  生命周期
    // ═══════════════════════════════════════════════════════════

    public void dispose() {
        if (quadMesh != null) quadMesh.dispose();
        if (shader != null) shader.dispose();
        if (instanceVBO != 0) {
            IntBuffer buf = BufferUtils.newByteBuffer(4).order(ByteOrder.nativeOrder()).asIntBuffer();
            buf.put(0, instanceVBO);
            Gdx.gl32.glDeleteBuffers(1, buf);
            instanceVBO = 0;
        }
        built = false;
    }
}