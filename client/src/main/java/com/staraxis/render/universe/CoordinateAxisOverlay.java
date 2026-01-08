package com.staraxis.render.universe;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;

/**
 * 绘制世界坐标轴（红 X / 绿 Y / 蓝 Z）。
 * 随相机缩放自适应轴线长度，确保在屏幕中始终可见但不过长。
 */
public final class CoordinateAxisOverlay {

    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private boolean visible = false;

    /**
     * 在渲染阶段调用。
     * @param projectionMatrix 来自 SpriteBatch 或相机的矩阵
     * @param kmPerPx          当前 km→px 比例，用于适配轴长
     */
    public void render(com.badlogic.gdx.math.Matrix4 projectionMatrix, float kmPerPx) {
        if (!visible) return;
        shapeRenderer.setProjectionMatrix(projectionMatrix);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        float axisLengthPx = computeAxisLengthPx(kmPerPx);

        // X: red
        shapeRenderer.setColor(Color.RED);
        shapeRenderer.line(0, 0, axisLengthPx, 0);
        // Y: green
        shapeRenderer.setColor(Color.GREEN);
        shapeRenderer.line(0, 0, 0, axisLengthPx);
        // Z: blue (绘制 45° 对角线表示 Z，仅示意)
        shapeRenderer.setColor(Color.BLUE);
        shapeRenderer.line(0, 0, axisLengthPx * 0.7f, axisLengthPx * 0.7f);

        shapeRenderer.end();
    }

    private float computeAxisLengthPx(float kmPerPx) {
        // 使轴长约等于视口宽/10，限制最小 50px，最大 200px
        float px = (float) (10000 / kmPerPx); // 选用 10,000 km 作为基准
        return Math.max(50, Math.min(200, px));
    }

    public void toggle() { visible = !visible; }
    public void setVisible(boolean v) { this.visible = v; }
    public boolean isVisible() { return visible; }

    public void dispose() { shapeRenderer.dispose(); }
}
