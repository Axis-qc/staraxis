package staraxis.render.debug;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * NormalDebugRenderer（模型法向调试渲染器）。
 *
 * 从 ModelInstance 的顶点缓冲读取顶点位置与法线，沿法线方向绘制线段，
 * 用于验证模型法线方向是否正确（例如球体法线朝外/朝内、明暗反转排查）。
 * 线段长度为固定 20 GU，不随模型缩放变化，便于统一观察。
 *
 * 开启方式：由 SystemViewRenderer 的调试开关控制（F3 调试面板「模型法向」按钮）。
 */
public final class NormalDebugRenderer {

    /** 法向线段固定长度（世界单位 GU），不随模型缩放变化。 */
    private static final float NORMAL_LENGTH = 20f;

    /** 法向线段颜色（亮青色，与场景主色区分）。 */
    private static final Color NORMAL_COLOR = new Color(0.3f, 1f, 1f, 1f);

    private final ShapeRenderer shapeRenderer;

    /** 临时向量，避免每帧分配。 */
    private final Vector3 tmpPos = new Vector3();
    private final Vector3 tmpNormal = new Vector3();

    public NormalDebugRenderer() {
        shapeRenderer = new ShapeRenderer();
    }

    public void dispose() {
        shapeRenderer.dispose();
    }

    /**
     * 渲染指定模型实例的全部网格法向线段。
     *
     * @param instance 模型实例（transform 须已更新到当前帧）
     * @param combined 相机 combined 矩阵（投影 x 视图）
     */
    public void render(ModelInstance instance, Matrix4 combined) {
        if (instance == null) {
            return;
        }
        shapeRenderer.setProjectionMatrix(combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(NORMAL_COLOR);
        renderNodes(instance, instance.nodes);
        shapeRenderer.end();
    }

    /** 递归遍历节点，渲染所有 NodePart 的网格法向。 */
    private void renderNodes(ModelInstance instance, Iterable<Node> nodes) {
        for (Node node : nodes) {
            if (node.parts != null) {
                for (NodePart part : node.parts) {
                    renderMesh(instance, part.meshPart);
                }
            }
            if (node.hasChildren()) {
                renderNodes(instance, node.getChildren());
            }
        }
    }

    /** 渲染单个 MeshPart 的法向线段（顶点缓冲 → 世界空间线段）。 */
    private void renderMesh(ModelInstance instance, MeshPart meshPart) {
        Mesh mesh = meshPart.mesh;
        VertexAttributes attrs = mesh.getVertexAttributes();
        int posOffset = -1;
        int nrmOffset = -1;
        for (int i = 0; i < attrs.size(); i++) {
            VertexAttribute attr = attrs.get(i);
            if (attr.usage == VertexAttributes.Usage.Position) {
                posOffset = attr.offset / 4;
            } else if (attr.usage == VertexAttributes.Usage.Normal) {
                nrmOffset = attr.offset / 4;
            }
        }
        if (posOffset < 0 || nrmOffset < 0) {
            return;
        }

        int stride = mesh.getVertexSize() / 4;
        int vertexCount = mesh.getNumVertices();
        float[] vertices = new float[vertexCount * stride];
        mesh.getVertices(vertices);

        // 线段固定长度，便于不同大小模型统一观察法向方向
        float length = NORMAL_LENGTH;

        for (int i = 0; i < vertexCount; i++) {
            int base = i * stride;
            tmpPos.set(vertices[base + posOffset], vertices[base + posOffset + 1],
                    vertices[base + posOffset + 2]);
            tmpPos.mul(instance.transform);

            tmpNormal.set(vertices[base + nrmOffset], vertices[base + nrmOffset + 1],
                    vertices[base + nrmOffset + 2]);
            tmpNormal.nor().rot(instance.transform).scl(length);

            shapeRenderer.line(tmpPos, tmpNormal.add(tmpPos));
        }
    }
}
