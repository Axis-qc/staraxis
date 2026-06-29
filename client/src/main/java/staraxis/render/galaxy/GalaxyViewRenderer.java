package staraxis.render.galaxy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;

import staraxis.game.space.galaxy.GalaxyData;
import staraxis.game.space.galaxy.SpectralType;
import staraxis.game.space.galaxy.StarPosition;
import staraxis.render.WorldCamera;

/**
 * GalaxyViewRenderer（星系视图渲染器）。
 *
 * 渲染 GalaxyData 中的所有恒星为光点。
 * 使用 StarBatchRenderer 批量渲染以提高性能。
 *
 * 恒星位置直接使用 StarPosition 中的 galaxyX/Y/Z。
 * 恒星颜色由 SpectralType 决定。
 */
public class GalaxyViewRenderer {

    private final ShapeRenderer shapeRenderer;
    private final StarBatchRenderer batchRenderer;
    private boolean useBatch = true;

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
        Matrix4 projectionView = camera.camera.combined;

        if (useBatch) {
            renderBatch(galaxy, projectionView, hoveredStarId);
        } else {
            renderShape(galaxy, projectionView, hoveredStarId);
        }
    }

    /**
     * 使用批量渲染（高性能）。
     */
    private void renderBatch(GalaxyData galaxy, Matrix4 projectionView, long hoveredStarId) {
        batchRenderer.begin(projectionView);
        for (StarPosition star : galaxy.stars) {
            SpectralType type = star.spectralType();
            boolean hovered = (star.starId() == hoveredStarId);
            batchRenderer.drawStar(
                (float) star.galaxyX(),
                (float) star.galaxyY(),
                (float) star.galaxyZ(),
                type.colorR, type.colorG, type.colorB,
                hovered
            );
        }
        batchRenderer.end();

        // 悬停高亮框
        if (hoveredStarId >= 0) {
            drawSelectionBox(galaxy.getStar(hoveredStarId), projectionView);
        }
    }

    /**
     * 使用 ShapeRenderer 渲染（低性能，备用）。
     */
    private void renderShape(GalaxyData galaxy, Matrix4 projectionView, long hoveredStarId) {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        shapeRenderer.setProjectionMatrix(projectionView);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Point);

        for (StarPosition star : galaxy.stars) {
            SpectralType type = star.spectralType();
            shapeRenderer.setColor(type.colorR, type.colorG, type.colorB, 1f);
            shapeRenderer.point(
                (float) star.galaxyX(),
                (float) star.galaxyY(),
                (float) star.galaxyZ()
            );
        }

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

        // 悬停高亮框
        if (hoveredStarId >= 0) {
            drawSelectionBox(galaxy.getStar(hoveredStarId), projectionView);
        }
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
        // ShapeRenderer 不需要 resize
    }

    public void dispose() {
        shapeRenderer.dispose();
        batchRenderer.dispose();
    }

    /**
     * 设置是否使用批量渲染。
     */
    public void setUseBatch(boolean useBatch) {
        this.useBatch = useBatch;
    }
}
