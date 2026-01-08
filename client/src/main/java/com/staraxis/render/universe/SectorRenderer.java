package com.staraxis.render.universe;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.staraxis.universegen.model.Sector;
import java.util.Random;

/**
 * SectorRenderer 负责在星区视图中渲染恒星分布；
 * 为演示目的，若 Sector 未携带恒星坐标，则根据 sectorId 和固定种子派生伪随机位置。
 */
public final class SectorRenderer {

    private static final int STAR_COUNT_DEFAULT = 128;

    private final ShapeRenderer shapeRenderer = new ShapeRenderer();

    /**
     * 根据当前缩放 (km→px 比例) 在屏幕绘制星区内恒星；
     */
    public void render(SpriteBatch batch, Sector sector, float kmPerPx) {
        long seed = sector.id() * 31L + 17L;
        Random rng = new Random(seed);

        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.WHITE);

        // 简易：星区范围 ±sectorHalfSizeKm，在局部坐标生成恒星
        double sectorHalfSizeKm = 0.5 * 9.4607e12; // 假 0.5 ly → km（演示用）
        for (int i = 0; i < STAR_COUNT_DEFAULT; i++) {
            double xKm = (rng.nextDouble() * 2 - 1) * sectorHalfSizeKm;
            double yKm = (rng.nextDouble() * 2 - 1) * sectorHalfSizeKm;
            float xPx = (float) (xKm / kmPerPx);
            float yPx = (float) (yKm / kmPerPx);
            shapeRenderer.circle(xPx, yPx, 1f);
        }
        shapeRenderer.end();
    }

    public void dispose() {
        shapeRenderer.dispose();
    }
}
