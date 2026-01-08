package com.staraxis.render.universe;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.staraxis.universegen.model.Galaxy;
import com.staraxis.universegen.model.Sector;

/**
 * 极简 UniverseRenderer：
 * 1. 按给定 zoom 值计算 km→px 比例；
 * 2. 仅渲染 Sector 质心为小点；
 * 3. 确保当 zoom==1 时 1px = 1km。
 */
public class UniverseRenderer {

    private static final float KM_PER_PX_AT_MAX_ZOOM = 1f; // 1 px = 1 km

    private Galaxy galaxy;
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();

    public void setGalaxy(Galaxy galaxy) {
        this.galaxy = galaxy;
    }

    /**
     * @param batch SpriteBatch (未使用，预留纹理渲染)
     * @param zoom  当前缩放 (1 = 最靠近; 越大越远)
     */
    public void render(SpriteBatch batch, float zoom) {
        if (galaxy == null) return;
        float kmPerPx = KM_PER_PX_AT_MAX_ZOOM * zoom; // 简化线性比例
        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.WHITE);
        for (Sector s : galaxy.sectors()) {
            // 假设 Sector id 映射到坐标 (id*1000 km, id*1000 km)
            float xPx = (float) (s.id() * 1000 / kmPerPx);
            float yPx = (float) (s.id() * 1000 / kmPerPx);
            shapeRenderer.circle(xPx, yPx, 2);
        }
        shapeRenderer.end();
    }

    public void dispose() {
        shapeRenderer.dispose();
    }
}
