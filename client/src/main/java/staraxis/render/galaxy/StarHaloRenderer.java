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
            + "uniform float u_lodFar;\n"
            + "uniform float u_shrinkFar;\n"
            + "varying vec3 v_color;\n"
            + "varying vec2 v_uv;\n"
            + "varying float v_intensity;\n"
            + "varying float v_discard;\n"
            + "void main() {\n"
            + "  float dist = distance(i_position, u_cameraPos);\n"
            //  光晕尺寸：d=lodFar 缩到球大小(minSizeScale)，d=shrinkFar 满尺寸(maxSizeScale)，区间内线性
            + "  float sizeT = clamp((dist - u_lodFar) / (u_shrinkFar - u_lodFar), 0.0, 1.0);\n"
            + "  float sizeScale = mix(u_minSizeScale, u_maxSizeScale, sizeT);\n"
            //  billboard：角点沿相机 right/up 展开，加到实例位置
            + "  vec3 worldPos = i_position + (a_corner.x * u_camRight + a_corner.y * u_camUp) * i_size * sizeScale;\n"
            + "  gl_Position = u_projViewTrans * vec4(worldPos, 1.0);\n"
            + "  v_color = i_color;\n"
            + "  v_uv = a_corner;\n"
            //  距离补偿提亮：远处 boost 大，近处小
            + "  v_intensity = clamp(dist / u_refDist, u_minBoost, u_maxBoost);\n"
            //  LOD 硬切换：d<lodFar 不画光晕（球出现），d>=lodFar 光晕出现（球消失）
            + "  v_discard = step(u_lodFar, dist);\n"
            + "}\n";

    //  片段着色器：模糊扩散层——全范围柔和渐变，提供整体星系明亮扩散感
    private static final String FRAG_GLOW = ""
            + "#version 120\n"
            + "varying vec3 v_color;\n"
            + "varying vec2 v_uv;\n"
            + "varying float v_intensity;\n"
            + "varying float v_discard;\n"
            + "void main() {\n"
            + "  if (v_discard < 0.5) discard;\n"
            + "  float d = length(v_uv);\n"
            //  柔和渐变：从中心到边缘全范围平滑过渡
            + "  float alpha = smoothstep(1.0, 0.0, d) * 0.6 * v_intensity;\n"
            + "  gl_FragColor = vec4(v_color * alpha, alpha);\n"
            + "}\n";

    //  片段着色器：锐利发光点层——亮核+外晕，提供清晰发光点
    private static final String FRAG_CORE = ""
            + "#version 120\n"
            + "varying vec3 v_color;\n"
            + "varying vec2 v_uv;\n"
            + "varying float v_intensity;\n"
            + "varying float v_discard;\n"
            + "void main() {\n"
            + "  if (v_discard < 0.5) discard;\n"
            + "  float d = length(v_uv);\n"
            //  亮核：d<0.3 满亮，0.3~0.5 快速衰减到 0
            + "  float core = smoothstep(0.5, 0.3, d);\n"
            //  外晕：d=0.4 起衰减到 1.0 归零，强度为亮核一半
            + "  float halo = smoothstep(1.0, 0.4, d) * 0.5;\n"
            + "  float alpha = (core + halo) * v_intensity;\n"
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
    //  两套 shader program 共享顶点着色器，fragment 不同（模糊扩散层 vs 锐利发光点层）
    private ShaderProgram glowShader;  // 模糊扩散层
    private ShaderProgram coreShader;  // 锐利发光点层
    private int uProjViewTrans_g, uCamRight_g, uCamUp_g, uCameraPos_g, uRefDist_g, uMinBoost_g, uMaxBoost_g, uMinSizeScale_g, uMaxSizeScale_g, uLodFar_g, uShrinkFar_g;
    private int uProjViewTrans_c, uCamRight_c, uCamUp_c, uCameraPos_c, uRefDist_c, uMinBoost_c, uMaxBoost_c, uMinSizeScale_c, uMaxSizeScale_c, uLodFar_c, uShrinkFar_c;
    private int iPositionLoc_g, iColorLoc_g, iSizeLoc_g;
    private int iPositionLoc_c, iColorLoc_c, iSizeLoc_c;

    // ── 距离补偿参数（可运行时调） ──────────────────────────────
    private float refDist = 4000f;   // 满亮参考距离（两层共享）

    //  模糊扩散层独立参数
    private float glowMinBoost = 0.8f;   // 模糊层近处亮度下限
    private float glowMaxBoost = 1.0f;  // 模糊层远处亮度上限
    private float glowMinSizeScale = 1.0f; // 模糊层缩到恒星球大小
    private float glowMaxSizeScale = 3.0f; // 模糊层满尺寸上限

    //  锐利发光点层独立参数
    private float coreMinBoost = 0.2f;   // 亮核层近处亮度下限
    private float coreMaxBoost = 0.5f;  // 亮核层远处亮度上限
    private float coreMinSizeScale = 0.6f; // 亮核层缩到恒星球大小（更小，清晰点）
    private float coreMaxSizeScale = 1.0f; // 亮核层满尺寸上限

    // ── LOD 距离阈值（模糊扩散层与锐利发光点层独立） ──
    private float glowLodFar = 10f;    // 模糊层：近于此距离光晕缩到球大小
    private float glowShrinkFar = 6000f; // 模糊层：远于此距离光晕满尺寸

    private float coreLodFar = 5000f;    // 亮核层：近于此距离光晕缩到球大小
    private float coreShrinkFar = 8000f; // 亮核层：远于此距离光晕满尺寸

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
        //  模糊扩散层
        glowShader = new ShaderProgram(VERT_SHADER, FRAG_GLOW);
        if (!glowShader.isCompiled()) {
            throw new GdxRuntimeException("恒星光晕(模糊层)着色器编译失败: " + glowShader.getLog());
        }
        uProjViewTrans_g = glowShader.getUniformLocation("u_projViewTrans");
        uCamRight_g = glowShader.getUniformLocation("u_camRight");
        uCamUp_g = glowShader.getUniformLocation("u_camUp");
        uCameraPos_g = glowShader.getUniformLocation("u_cameraPos");
        uRefDist_g = glowShader.getUniformLocation("u_refDist");
        uMinBoost_g = glowShader.getUniformLocation("u_minBoost");
        uMaxBoost_g = glowShader.getUniformLocation("u_maxBoost");
        uMinSizeScale_g = glowShader.getUniformLocation("u_minSizeScale");
        uMaxSizeScale_g = glowShader.getUniformLocation("u_maxSizeScale");
        uLodFar_g = glowShader.getUniformLocation("u_lodFar");
        uShrinkFar_g = glowShader.getUniformLocation("u_shrinkFar");
        iPositionLoc_g = glowShader.getAttributeLocation("i_position");
        iColorLoc_g = glowShader.getAttributeLocation("i_color");
        iSizeLoc_g = glowShader.getAttributeLocation("i_size");

        //  锐利发光点层
        coreShader = new ShaderProgram(VERT_SHADER, FRAG_CORE);
        if (!coreShader.isCompiled()) {
            throw new GdxRuntimeException("恒星光晕(亮核层)着色器编译失败: " + coreShader.getLog());
        }
        uProjViewTrans_c = coreShader.getUniformLocation("u_projViewTrans");
        uCamRight_c = coreShader.getUniformLocation("u_camRight");
        uCamUp_c = coreShader.getUniformLocation("u_camUp");
        uCameraPos_c = coreShader.getUniformLocation("u_cameraPos");
        uRefDist_c = coreShader.getUniformLocation("u_refDist");
        uMinBoost_c = coreShader.getUniformLocation("u_minBoost");
        uMaxBoost_c = coreShader.getUniformLocation("u_maxBoost");
        uMinSizeScale_c = coreShader.getUniformLocation("u_minSizeScale");
        uMaxSizeScale_c = coreShader.getUniformLocation("u_maxSizeScale");
        uLodFar_c = coreShader.getUniformLocation("u_lodFar");
        uShrinkFar_c = coreShader.getUniformLocation("u_shrinkFar");
        iPositionLoc_c = coreShader.getAttributeLocation("i_position");
        iColorLoc_c = coreShader.getAttributeLocation("i_color");
        iSizeLoc_c = coreShader.getAttributeLocation("i_size");
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

    /** 渲染全部恒星光晕。两层各一次 instanced draw call，additive 混合。 */
    public void render(WorldCamera camera) {
        if (!built || instanceCount == 0) return;

        //  billboard 需要相机 right/up 向量：right = direction × up
        tmpRight.set(camera.camera.direction).crs(camera.camera.up).nor();
        tmpUp.set(camera.camera.up).nor();

        //  光晕开启深度测试（被前方恒星球遮挡）、关闭深度写入（不污染深度缓冲，光晕间 additive 叠加）
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(false);
        //  additive 混合：颜色相加，产生明亮发光感
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

        //  先画模糊扩散层（大、弱），再画锐利发光点层（小、强），additive 叠加
        renderLayer(glowShader, uProjViewTrans_g, uCamRight_g, uCamUp_g, uCameraPos_g, uRefDist_g,
                uMinBoost_g, uMaxBoost_g, uMinSizeScale_g, uMaxSizeScale_g, uLodFar_g, uShrinkFar_g,
                iPositionLoc_g, iColorLoc_g, iSizeLoc_g,
                glowMinBoost, glowMaxBoost, glowMinSizeScale, glowMaxSizeScale, camera,
                glowLodFar, glowShrinkFar);
        renderLayer(coreShader, uProjViewTrans_c, uCamRight_c, uCamUp_c, uCameraPos_c, uRefDist_c,
                uMinBoost_c, uMaxBoost_c, uMinSizeScale_c, uMaxSizeScale_c, uLodFar_c, uShrinkFar_c,
                iPositionLoc_c, iColorLoc_c, iSizeLoc_c,
                coreMinBoost, coreMaxBoost, coreMinSizeScale, coreMaxSizeScale, camera,
                coreLodFar, coreShrinkFar);

        //  恢复混合/深度状态
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDepthFunc(GL20.GL_LESS);
    }

    /** 渲染单层光晕（一次 instanced draw call） */
    private void renderLayer(ShaderProgram sh, int uProj, int uRight, int uUp, int uCamPos, int uRef,
                             int uMinB, int uMaxB, int uMinS, int uMaxS, int uLod, int uShrink,
                             int iPos, int iCol, int iSz,
                             float minBoost, float maxBoost, float minSizeScale, float maxSizeScale,
                             WorldCamera camera,
                             float lodVal, float shrinkVal) {
        sh.bind();
        sh.setUniformMatrix(uProj, camera.camera.combined);
        sh.setUniformf(uRight, tmpRight);
        sh.setUniformf(uUp, tmpUp);
        sh.setUniformf(uCamPos, camera.camera.position);
        sh.setUniformf(uRef, refDist);
        sh.setUniformf(uMinB, minBoost);
        sh.setUniformf(uMaxB, maxBoost);
        sh.setUniformf(uMinS, minSizeScale);
        sh.setUniformf(uMaxS, maxSizeScale);
        sh.setUniformf(uLod, lodVal);
        sh.setUniformf(uShrink, shrinkVal);

        quadMesh.bind(sh);

        //  绑定实例属性
        Gdx.gl32.glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        Gdx.gl32.glEnableVertexAttribArray(iPos);
        Gdx.gl32.glVertexAttribPointer(iPos, 3, GL_FLOAT, false, BYTES_PER_INSTANCE, 0);
        Gdx.gl32.glVertexAttribDivisor(iPos, 1);
        Gdx.gl32.glEnableVertexAttribArray(iCol);
        Gdx.gl32.glVertexAttribPointer(iCol, 3, GL_FLOAT, false, BYTES_PER_INSTANCE, 12);
        Gdx.gl32.glVertexAttribDivisor(iCol, 1);
        Gdx.gl32.glEnableVertexAttribArray(iSz);
        Gdx.gl32.glVertexAttribPointer(iSz, 1, GL_FLOAT, false, BYTES_PER_INSTANCE, 24);
        Gdx.gl32.glVertexAttribDivisor(iSz, 1);

        Gdx.gl32.glDrawElementsInstanced(
                GL_TRIANGLES,
                quadMesh.getNumIndices(),
                GL_UNSIGNED_SHORT,
                0,
                instanceCount);

        //  清理本层属性绑定
        Gdx.gl32.glDisableVertexAttribArray(iPos);
        Gdx.gl32.glDisableVertexAttribArray(iCol);
        Gdx.gl32.glDisableVertexAttribArray(iSz);
        Gdx.gl32.glBindBuffer(GL_ARRAY_BUFFER, 0);
        quadMesh.unbind(sh);
    }

    public boolean isBuilt() {
        return built;
    }

    // ═══════════════════════════════════════════════════════════
    //  生命周期
    // ═══════════════════════════════════════════════════════════

    public void dispose() {
        if (quadMesh != null) quadMesh.dispose();
        if (glowShader != null) glowShader.dispose();
        if (coreShader != null) coreShader.dispose();
        if (instanceVBO != 0) {
            IntBuffer buf = BufferUtils.newByteBuffer(4).order(ByteOrder.nativeOrder()).asIntBuffer();
            buf.put(0, instanceVBO);
            Gdx.gl32.glDeleteBuffers(1, buf);
            instanceVBO = 0;
        }
        built = false;
    }
}