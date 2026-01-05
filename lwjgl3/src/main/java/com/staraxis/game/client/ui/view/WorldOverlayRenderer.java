package com.staraxis.game.client.ui.view;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.staraxis.game.shared.world.WorldMap;

/**
 * 世界覆盖层渲染器 (World Overlay Renderer). 负责绘制网格之上的动态标记、单位、特效占位等。
 */
public class WorldOverlayRenderer {

    private final ShapeRenderer shapeRenderer;

    public WorldOverlayRenderer() {
        this.shapeRenderer = new ShapeRenderer();
    }

    public void render(WorldMap worldMap, Camera camera) {
        // 目前仅作为顶层渲染的占位 (T052)
        // 后续可以在这里添加选中的边框、范围提示等
    }

    public void setProjectionMatrix(com.badlogic.gdx.math.Matrix4 matrix) {
        shapeRenderer.setProjectionMatrix(matrix);
    }

    public void dispose() {
        shapeRenderer.dispose();
    }
}
