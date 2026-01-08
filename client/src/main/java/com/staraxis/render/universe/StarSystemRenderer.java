package com.staraxis.render.universe;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.staraxis.universegen.model.Planet;
import com.staraxis.universegen.model.StarSystem;

/**
 * StarSystemRenderer 在恒星系视图中渲染行星及其轨道。
 * 规则：
 * 1. 轨道近似圆形，半径 = semiMajorAxisKm / kmPerPx；
 * 2. 行星绘制为轨道上的小圆，半径 = radiusKm / kmPerPx（最小像素保证）。
 * 3. 当前仅渲染静态位置（相位随机），后续可扩展为按时间推进。
 */
public final class StarSystemRenderer {

    private static final float MIN_PLANET_PX = 2f; // 最小行星像素半径
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();

    public void render(SpriteBatch batch, StarSystem system, float kmPerPx) {
        if (system == null) return;
        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());

        // 轨道
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.LIGHT_GRAY);
        for (Planet p : system.planets()) {
            float rPx = (float) (p.semiMajorAxisKm() / kmPerPx);
            shapeRenderer.circle(0, 0, rPx);
        }
        shapeRenderer.end();

        // 行星
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.CYAN);
        int idx = 0;
        for (Planet p : system.planets()) {
            float rOrbitPx = (float) (p.semiMajorAxisKm() / kmPerPx);
            // 为简单起见，行星分散到不同角度
            double angle = (idx++ * 37) % 360; // 伪随机角度
            float x = (float) (rOrbitPx * Math.cos(Math.toRadians(angle)));
            float y = (float) (rOrbitPx * Math.sin(Math.toRadians(angle)));
            float planetPx = Math.max(MIN_PLANET_PX, (float) (p.radiusKm() / kmPerPx));
            shapeRenderer.circle(x, y, planetPx);
        }
        shapeRenderer.end();
    }

    public void dispose() {
        shapeRenderer.dispose();
    }
}
