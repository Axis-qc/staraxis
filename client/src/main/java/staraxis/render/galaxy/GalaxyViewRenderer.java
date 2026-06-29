package staraxis.render.galaxy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.collision.Ray;

import staraxis.game.space.galaxy.GalaxyData;
import staraxis.game.space.galaxy.StarPosition;
import staraxis.render.WorldCamera;

/**
 * GalaxyViewRenderer（星系视图渲染器）。
 *
 * 渲染 GalaxyData 中的所有恒星。
 * 使用 StarBatchRenderer 批量渲染（每种光谱类型一个球体模型）。
 *
 * 恒星位置直接使用 StarPosition 中的 galaxyX/Y/Z。
 * 恒星颜色由 SpectralType 决定。
 */
public class GalaxyViewRenderer {

    private final ShapeRenderer shapeRenderer;
    private final StarBatchRenderer batchRenderer;

    public GalaxyViewRenderer() {
        this.shapeRenderer = new ShapeRenderer();
        this.batchRenderer = new StarBatchRenderer();
    }

    /**
     * 渲染整个星系。
     *
     * @param galaxy 星系数据
     * @param camera 世界相机
     * @param hoveredStarId 悬停的恒星ID（-1 表示无）
     */
    public void render(GalaxyData galaxy, WorldCamera camera, long hoveredStarId) {
        batchRenderer.render(galaxy, camera, hoveredStarId);

        // 悬停高亮框
        if (hoveredStarId >= 0) {
            drawSelectionBox(galaxy.getStar(hoveredStarId), camera.camera.combined);
        }
    }

    /**
     * 射线拾取恒星。
     *
     * @param ray 鼠标射线
     * @param galaxy 星系数据
     * @return 命中的恒星ID，未命中返回 -1
     */
    public long pick(Ray ray, GalaxyData galaxy) {
        return batchRenderer.pick(ray, galaxy);
    }

    /**
     * 绘制选中框。
     */
    private void drawSelectionBox(StarPosition star, Matrix4 projectionView) {
        if (star == null) return;

        float x = (float) star.galaxyX();
        float y = (float) star.galaxyY();
        float z = (float) star.galaxyZ();
        float s = 200f;

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        shapeRenderer.setProjectionMatrix(projectionView);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.3f, 0.8f, 1f, 0.8f);
        Gdx.gl.glLineWidth(2f);

        // 方形框
        shapeRenderer.line(x - s, y, z - s, x + s, y, z - s);
        shapeRenderer.line(x + s, y, z - s, x + s, y, z + s);
        shapeRenderer.line(x + s, y, z + s, x - s, y, z + s);
        shapeRenderer.line(x - s, y, z + s, x - s, y, z - s);

        shapeRenderer.end();
    }

    public void resize(int w, int h) {
        // 不需要 resize
    }

    public void dispose() {
        shapeRenderer.dispose();
        batchRenderer.dispose();
    }
}
