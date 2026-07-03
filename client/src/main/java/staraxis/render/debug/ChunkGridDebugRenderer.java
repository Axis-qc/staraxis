package staraxis.render.debug;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import staraxis.game.space.SpacePosition;

/**
 * ChunkGridDebugRenderer（区块网格调试渲染器）。
 *
 * 在 System View 中绘制区块边界网格线，用于验证区块坐标是否正确分布。
 * 网格居中于相机位置所在的区块，跟随镜头移动。
 *
 * 开启：构造后调用 render()，关闭：null 掉引用或不调用。
 */
public class ChunkGridDebugRenderer {

    private static final float CHUNK = (float) SpacePosition.CHUNK_SIZE;

    private final ShapeRenderer shapeRenderer;
    private boolean enabled = true;

    /** 网格可见范围（区块数），默认 ±5 个区块（覆盖 100k GU 范围）。 */
    private int range = 5;

    /** 网格透明度。 */
    private float alpha = 0.25f;

    public ChunkGridDebugRenderer() {
        shapeRenderer = new ShapeRenderer();
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }

    public void setRange(int range) { this.range = range; }
    public void setAlpha(float alpha) { this.alpha = alpha; }

    /**
     * 渲染区块网格线，居中于相机位置。
     *
     * @param combined 相机的 combined 矩阵
     * @param cameraPos 相机位置（世界坐标）
     */
    public void render(Matrix4 combined, Vector3 cameraPos) {
        if (!enabled) return;

        // 计算相机所在的区块坐标
        int centerCx = (int) Math.floor(cameraPos.x / CHUNK);
        int centerCy = (int) Math.floor(cameraPos.y / CHUNK);
        int centerCz = (int) Math.floor(cameraPos.z / CHUNK);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glLineWidth(1f);

        shapeRenderer.setProjectionMatrix(combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(new Color(0.5f, 0.5f, 0.6f, alpha));

        int startCx = centerCx - range;
        int endCx = centerCx + range + 1;
        int startCy = centerCy - range;
        int endCy = centerCy + range + 1;
        int startCz = centerCz - range;
        int endCz = centerCz + range + 1;

        // X 方向线：固定 Y 和 Z，改变 X
        for (int cy = startCy; cy <= endCy; cy++) {
            float y = cy * CHUNK;
            for (int cz = startCz; cz <= endCz; cz++) {
                float z = cz * CHUNK;
                float xStart = startCx * CHUNK;
                float xEnd = endCx * CHUNK;
                shapeRenderer.line(xStart, y, z, xEnd, y, z);
            }
        }

        // Y 方向线：固定 X 和 Z，改变 Y
        for (int cx = startCx; cx <= endCx; cx++) {
            float x = cx * CHUNK;
            for (int cz = startCz; cz <= endCz; cz++) {
                float z = cz * CHUNK;
                float yStart = startCy * CHUNK;
                float yEnd = endCy * CHUNK;
                shapeRenderer.line(x, yStart, z, x, yEnd, z);
            }
        }

        // Z 方向线：固定 X 和 Y，改变 Z
        for (int cx = startCx; cx <= endCx; cx++) {
            float x = cx * CHUNK;
            for (int cy = startCy; cy <= endCy; cy++) {
                float y = cy * CHUNK;
                float zStart = startCz * CHUNK;
                float zEnd = endCz * CHUNK;
                shapeRenderer.line(x, y, zStart, x, y, zEnd);
            }
        }

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * 在指定区块坐标位置标注一个十字标记。
     */
    public void markChunk(int cx, int cy, int cz, Matrix4 combined, Color color) {
        float halfChunk = CHUNK / 2f;
        float cxPos = cx * CHUNK + halfChunk;
        float cyPos = cy * CHUNK + halfChunk;
        float czPos = cz * CHUNK + halfChunk;
        float markSize = CHUNK * 0.1f;

        shapeRenderer.setProjectionMatrix(combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(color);

        // 十字标记
        shapeRenderer.line(cxPos - markSize, cyPos, czPos, cxPos + markSize, cyPos, czPos);
        shapeRenderer.line(cxPos, cyPos - markSize, czPos, cxPos, cyPos + markSize, czPos);
        shapeRenderer.line(cxPos, cyPos, czPos - markSize, cxPos, cyPos, czPos + markSize);

        shapeRenderer.end();
    }

    public void dispose() {
        shapeRenderer.dispose();
    }
}
