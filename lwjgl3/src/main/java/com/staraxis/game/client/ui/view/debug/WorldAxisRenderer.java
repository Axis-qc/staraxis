package com.staraxis.game.client.ui.view.debug;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * F3 世界坐标轴渲染（2D 视角）：仅绘制 X/Y 轴。
 *
 * 需求：
 * - 坐标轴应像网格一样延伸（贯穿屏幕可见范围 + 余量），而不是固定长度。
 *
 * 约定：
 * - X 轴：红色（y = 0）
 * - Y 轴：绿色（x = 0）
 * - 以世界原点 (0,0) 为基准对齐
 */
public final class WorldAxisRenderer {

    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private boolean visible;

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(OrthographicCamera camera, com.staraxis.game.core.coordinate.CameraWorld camWorld) {
        if (!visible) {
            return;
        }

        // 与 WorldGridRenderer 一致：按相机可见范围绘制，并加 1.2x 余量
        double viewWidthWorld = camera.viewportWidth * camera.zoom;
        double viewHeightWorld = camera.viewportHeight * camera.zoom;

        double halfW = viewWidthWorld * 0.5 * 1.2;
        double halfH = viewHeightWorld * 0.5 * 1.2;

        double camX = camera.position.x;
        double camY = camera.position.y;

        double minX = camX - halfW;
        double maxX = camX + halfW;
        double minY = camY - halfH;
        double maxY = camY + halfH;

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        // X 轴：y=0，仅当 y=0 在当前视野范围内时绘制
        if (0.0 >= minY && 0.0 <= maxY) {
            shapeRenderer.setColor(Color.RED);
            shapeRenderer.line((float) minX, 0f, (float) maxX, 0f);
        }

        // Y 轴：x=0，仅当 x=0 在当前视野范围内时绘制
        if (0.0 >= minX && 0.0 <= maxX) {
            shapeRenderer.setColor(Color.GREEN);
            shapeRenderer.line(0f, (float) minY, 0f, (float) maxY);
        }

        shapeRenderer.end();
    }

    public void dispose() {
        shapeRenderer.dispose();
    }
}
