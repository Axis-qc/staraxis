package com.staraxis.game.client.ui.components;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * 视差背景类 (Parallax Background) 通过多层不同移动速度的贴图实现深度的视觉错觉。
 */
public class ParallaxBackground {

    private final Array<ParallaxLayer> layers;
    private final Viewport viewport;

    public ParallaxBackground(Viewport viewport) {
        this.viewport = viewport;
        this.layers = new Array<>();
    }

    public void addLayer(TextureRegion region, float scrollSpeedX, float scrollSpeedY) {
        layers.add(new ParallaxLayer(region, scrollSpeedX, scrollSpeedY));
    }

    public void render(Batch batch, float delta) {
        batch.setProjectionMatrix(viewport.getCamera().combined);

        float worldWidth = viewport.getWorldWidth();
        float worldHeight = viewport.getWorldHeight();

        for (ParallaxLayer layer : layers) {
            layer.update(delta);

            float regWidth = layer.region.getRegionWidth();
            float regHeight = layer.region.getRegionHeight();

            // 绘制层，支持循环平铺
            float x = (layer.offsetX % regWidth);
            if (x > 0) {
                x -= regWidth;
            }
            float y = (layer.offsetY % regHeight);
            if (y > 0) {
                y -= regHeight;
            }

            for (float ix = x; ix < worldWidth; ix += regWidth) {
                for (float iy = y; iy < worldHeight; iy += regHeight) {
                    batch.draw(layer.region, ix, iy, regWidth, regHeight);
                }
            }
        }
    }

    private static class ParallaxLayer {

        final TextureRegion region;
        final float scrollSpeedX;
        final float scrollSpeedY;
        float offsetX;
        float offsetY;

        ParallaxLayer(TextureRegion region, float scrollSpeedX, float scrollSpeedY) {
            this.region = region;
            this.scrollSpeedX = scrollSpeedX;
            this.scrollSpeedY = scrollSpeedY;
        }

        void update(float delta) {
            offsetX += scrollSpeedX * delta;
            offsetY += scrollSpeedY * delta;
        }
    }
}
