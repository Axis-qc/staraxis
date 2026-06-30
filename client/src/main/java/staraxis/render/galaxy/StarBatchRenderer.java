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
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;

import staraxis.game.entity.EntityType;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.game.state.snapshot.EntitySnapshot.StarDetails;
import staraxis.render.WorldCamera;
import staraxis.render.util.TemperatureColor;

/**
 * StarBatchRenderer（恒星批量渲染器）— GPU 实例化渲染。
 *
 * 恒星位置+颜色一次性上传到 GPU 实例缓冲区，每帧一次 glDrawElementsInstanced 绘制全部。
 * hover 变色时仅更新对应实例的颜色数据，无需 CPU 遍历或材质操作。
 */
public class StarBatchRenderer {

    private static final float STAR_RADIUS = 40f;

    // ── 着色器 ──────────────────────────────────────────────────
    private static final String VERT_SHADER = ""
            + "#version 120\n"
            + "attribute vec3 a_position;\n"
            + "attribute vec3 a_normal;\n"
            + "attribute vec3 i_position;\n"
            + "attribute vec3 i_color;\n"
            + "uniform mat4 u_projViewTrans;\n"
            + "uniform vec3 u_cameraPos;\n"
            + "uniform float u_lodFar;\n"
            + "varying vec3 v_color;\n"
            + "varying float v_discard;\n"
            + "void main() {\n"
            + "  vec4 pos = vec4(a_position + i_position, 1.0);\n"
            + "  gl_Position = u_projViewTrans * pos;\n"
            + "  v_color = i_color;\n"
            //  LOD 硬切换：d>=lodFar 直接 discard，球永远不透明写深度
            + "  float dist = distance(i_position, u_cameraPos);\n"
            + "  v_discard = step(dist, u_lodFar);\n"
            + "}\n";

    private static final String FRAG_SHADER = ""
            + "varying vec3 v_color;\n"
            + "varying float v_discard;\n"
            + "void main() {\n"
            //  超出 LOD 距离直接丢弃，不写颜色也不写深度
            + "  if (v_discard < 0.5) discard;\n"
            //  恒星是自发光体，直接输出颜色，不依赖任何场景光照
            + "  gl_FragColor = vec4(v_color, 1.0);\n"
            + "}\n";

    // ── 常量 ──────────────────────────────────────────────────
    private static final int FLOATS_PER_INSTANCE = 6; // pos(3) + color(3)
    private static final int BYTES_PER_INSTANCE = FLOATS_PER_INSTANCE * 4;

    // ── 几何体 ──────────────────────────────────────────────────
    private Mesh sphereMesh;

    // ── 实例数据 ──────────────────────────────────────────────────
    private int instanceVBO;
    private int instanceCount;
    private long[] starIds;
    private float[] baseColors;  // 原始颜色（恢复 hover 用）
    private float[] positions;   // CPU 端位置缓存（pick 用）

    // ── 着色器 ──────────────────────────────────────────────────
    private ShaderProgram shader;
    private int uProjViewTrans;
    private int uCameraPos;
    private int uLodFar;
    private int iPositionLoc;
    private int iColorLoc;

    // ── LOD 距离阈值（球硬切换，光晕软淡入） ──────────────────────
    private float lodFar = 5000f;   // 远于此距离：球 discard，光晕接管

    // ── 状态 ──────────────────────────────────────────────────
    private int hoveredIndex = -1;
    private boolean built;

    public StarBatchRenderer() {
        initShader();
        initSphereMesh();
        initInstanceBuffer();
    }

    // ═══════════════════════════════════════════════════════════
    //  初始化
    // ═══════════════════════════════════════════════════════════

    private void initShader() {
        shader = new ShaderProgram(VERT_SHADER, FRAG_SHADER);
        if (!shader.isCompiled()) {
            throw new GdxRuntimeException("恒星实例化着色器编译失败: " + shader.getLog());
        }
        uProjViewTrans = shader.getUniformLocation("u_projViewTrans");
        uCameraPos = shader.getUniformLocation("u_cameraPos");
        uLodFar = shader.getUniformLocation("u_lodFar");
        iPositionLoc = shader.getAttributeLocation("i_position");
        iColorLoc = shader.getAttributeLocation("i_color");
    }

    /** 生成 UV 球体网格（与原来 createSphere 参数一致：8 分段 × 6 层） */
    private void initSphereMesh() {
        int slices = 8, stacks = 6;
        int vCount = (slices + 1) * (stacks + 1);
        int iCount = slices * stacks * 6;

        float[] verts = new float[vCount * 6]; // pos3 + normal3
        short[] idx = new short[iCount];

        int vi = 0;
        for (int j = 0; j <= stacks; j++) {
            double phi = Math.PI * j / stacks;
            for (int i = 0; i <= slices; i++) {
                double theta = 2.0 * Math.PI * i / slices;
                float x = (float) (STAR_RADIUS * Math.sin(phi) * Math.cos(theta));
                float y = (float) (STAR_RADIUS * Math.cos(phi));
                float z = (float) (STAR_RADIUS * Math.sin(phi) * Math.sin(theta));
                verts[vi++] = x;
                verts[vi++] = y;
                verts[vi++] = z;
                float len = (float) Math.sqrt(x * x + y * y + z * z);
                verts[vi++] = x / len;
                verts[vi++] = y / len;
                verts[vi++] = z / len;
            }
        }

        int ii = 0;
        for (int j = 0; j < stacks; j++) {
            for (int i = 0; i < slices; i++) {
                int a = j * (slices + 1) + i;
                int b = a + slices + 1;
                idx[ii++] = (short) a;
                idx[ii++] = (short) b;
                idx[ii++] = (short) (a + 1);
                idx[ii++] = (short) b;
                idx[ii++] = (short) (b + 1);
                idx[ii++] = (short) (a + 1);
            }
        }

        sphereMesh = new Mesh(true, vCount, iCount,
                new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"));
        sphereMesh.setVertices(verts);
        sphereMesh.setIndices(idx);
    }

    private void initInstanceBuffer() {
        IntBuffer buf = BufferUtils.newByteBuffer(4).order(ByteOrder.nativeOrder()).asIntBuffer();
        Gdx.gl32.glGenBuffers(1, buf);
        instanceVBO = buf.get(0);
    }

    // ═══════════════════════════════════════════════════════════
    //  公开 API
    // ═══════════════════════════════════════════════════════════

    /** 从世界状态重建实例缓冲区（恒星预设不变，仅需要在首次渲染前调用一次） */
    public void rebuild(RealTimeWorldState state) {
        // 过滤出 STAR 类型实体
        java.util.ArrayList<EntitySnapshot> stars = new java.util.ArrayList<>();
        for (EntitySnapshot snap : state.getEntitySnapshotsView()) {
            if (snap != null && snap.entityType == EntityType.STAR) {
                stars.add(snap);
            }
        }

        int n = stars.size();
        instanceCount = n;
        starIds = new long[n];
        baseColors = new float[n * 3];
        positions = new float[n * 3];

        // 构建实例数据缓冲区：每实例 6 个 float（pos3 + color3）
        ByteBuffer bb = BufferUtils.newByteBuffer(n * BYTES_PER_INSTANCE);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();

        for (int i = 0; i < n; i++) {
            EntitySnapshot s = stars.get(i);
            starIds[i] = s.entityId;

            float px = (float) s.posWorldGU.x();
            float py = (float) s.posWorldGU.y();
            float pz = (float) s.posWorldGU.z();
            positions[i * 3] = px;
            positions[i * 3 + 1] = py;
            positions[i * 3 + 2] = pz;
            fb.put(px);
            fb.put(py);
            fb.put(pz);

            float[] rgb;
            if (s.details instanceof StarDetails sd) {
                rgb = TemperatureColor.temperatureToRgb(sd.temperatureK);
            } else {
                rgb = new float[]{1f, 0.92f, 0.6f};
            }
            baseColors[i * 3] = rgb[0];
            baseColors[i * 3 + 1] = rgb[1];
            baseColors[i * 3 + 2] = rgb[2];
            fb.put(rgb[0]);
            fb.put(rgb[1]);
            fb.put(rgb[2]);
        }
        fb.flip();

        // 上传到 GPU 实例 VBO
        Gdx.gl32.glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        Gdx.gl32.glBufferData(GL_ARRAY_BUFFER, bb.limit() * 4, bb, GL_STATIC_DRAW);
        Gdx.gl32.glBindBuffer(GL_ARRAY_BUFFER, 0);

        hoveredIndex = -1;
        built = true;
    }

    /** 渲染全部恒星。每帧仅一次 instanced draw call。 */
    public void render(WorldCamera camera, long hoveredStarId) {
        if (!built || instanceCount == 0) return;

        // hover 切换时只更新 GPU 中对应实例的颜色
        int newIdx = (hoveredStarId >= 0) ? indexOf(hoveredStarId) : -1;
        if (newIdx != hoveredIndex) {
            // 恢复旧 hover 的原始颜色
            if (hoveredIndex >= 0) {
                updateInstanceColor(hoveredIndex,
                        baseColors[hoveredIndex * 3],
                        baseColors[hoveredIndex * 3 + 1],
                        baseColors[hoveredIndex * 3 + 2]);
            }
            // 设置新 hover 为白色高亮
            if (newIdx >= 0) {
                updateInstanceColor(newIdx, 1f, 1f, 1f);
            }
            hoveredIndex = newIdx;
        }

        // ── 球不透明：开启深度测试 + 深度写入，正确遮挡后方球和光晕 ──
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
        //  不需要 alpha 混合（球永远不透明，远端用 discard 跳过）
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // ── 绑定着色器 ──
        shader.bind();
        shader.setUniformMatrix(uProjViewTrans, camera.camera.combined);
        shader.setUniformf(uCameraPos, camera.camera.position);
        shader.setUniformf(uLodFar, lodFar);

        // ── 绑定球体几何体（模型顶点属性） ──
        sphereMesh.bind(shader);

        // ── 绑定实例属性（divisor=1：每实例切换一次属性） ──
        Gdx.gl32.glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        Gdx.gl32.glEnableVertexAttribArray(iPositionLoc);
        Gdx.gl32.glVertexAttribPointer(iPositionLoc, 3, GL_FLOAT, false, BYTES_PER_INSTANCE, 0);
        Gdx.gl32.glVertexAttribDivisor(iPositionLoc, 1);
        Gdx.gl32.glEnableVertexAttribArray(iColorLoc);
        Gdx.gl32.glVertexAttribPointer(iColorLoc, 3, GL_FLOAT, false, BYTES_PER_INSTANCE, 12);
        Gdx.gl32.glVertexAttribDivisor(iColorLoc, 1);

        // ── 一次 instanced draw call 绘制全部恒星 ──
        Gdx.gl32.glDrawElementsInstanced(
                GL_TRIANGLES,
                sphereMesh.getNumIndices(),
                GL_UNSIGNED_SHORT,
                0,
                instanceCount);

        // ── 清理 ──
        Gdx.gl32.glDisableVertexAttribArray(iPositionLoc);
        Gdx.gl32.glDisableVertexAttribArray(iColorLoc);
        Gdx.gl32.glBindBuffer(GL_ARRAY_BUFFER, 0);
        sphereMesh.unbind(shader);
    }

    /** 射线拾取（CPU 端，复用缓存的位置数据，精度为球体相交检测） */
    public long pick(Ray ray) {
        if (!built || instanceCount == 0) return -1;

        long hitId = -1;
        float bestDist = Float.MAX_VALUE;
        Vector3 tmp = new Vector3();

        for (int i = 0; i < instanceCount; i++) {
            float cx = positions[i * 3];
            float cy = positions[i * 3 + 1];
            float cz = positions[i * 3 + 2];
            if (Intersector.intersectRaySphere(ray, tmp.set(cx, cy, cz), STAR_RADIUS, tmp)) {
                float d = ray.origin.dst(tmp);
                if (d < bestDist) {
                    bestDist = d;
                    hitId = starIds[i];
                }
            }
        }
        return hitId;
    }

    /** 根据恒星 entityId 获取世界坐标（供 selection box 绘制用） */
    public float[] getStarPosition(long starId) {
        if (!built) return null;
        int idx = indexOf(starId);
        if (idx < 0) return null;
        return new float[]{positions[idx * 3], positions[idx * 3 + 1], positions[idx * 3 + 2]};
    }

    public boolean isBuilt() {
        return built;
    }

    // ═══════════════════════════════════════════════════════════
    //  内部方法
    // ═══════════════════════════════════════════════════════════

    private int indexOf(long starId) {
        for (int i = 0; i < instanceCount; i++) {
            if (starIds[i] == starId) return i;
        }
        return -1;
    }

    /** 更新 GPU 实例缓冲区中某个实例的颜色（只更新 color 3 个 float） */
    private void updateInstanceColor(int index, float r, float g, float b) {
        ByteBuffer bb = BufferUtils.newByteBuffer(12);
        bb.order(ByteOrder.nativeOrder());
        bb.putFloat(r);
        bb.putFloat(g);
        bb.putFloat(b);
        bb.flip();

        Gdx.gl32.glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        Gdx.gl32.glBufferSubData(GL_ARRAY_BUFFER, index * BYTES_PER_INSTANCE + 12, 12, bb);
        Gdx.gl32.glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    // ═══════════════════════════════════════════════════════════
    //  生命周期
    // ═══════════════════════════════════════════════════════════

    public void dispose() {
        if (sphereMesh != null) sphereMesh.dispose();
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
