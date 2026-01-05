package com.staraxis.game.client.ui.view;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.WorldMap;

/**
 * 六边形网格渲染器 (Hex Grid Renderer). 负责绘制地图上的所有瓦片及其边框。
 */
public class HexGridRenderer {

    private final ShapeRenderer shapeRenderer;
    private float hexRadius = 50f; // 从中心到顶点的距离
    private final Vector2 tempVec = new Vector2();

    public HexGridRenderer() {
        this.shapeRenderer = new ShapeRenderer();
    }

    /**
     * 渲染整个地图，包含 LOD 与视口剔除。
     */
    public void render(WorldMap worldMap, HexCoord highlightedCoord, float zoom, Camera camera) {
        if (worldMap == null) {
            return;
        }

        ZoomLevel level = ZoomLevel.fromZoom(zoom);

        // 1. 仅渲染边框 (根据用户请求：不要填充颜色，仅绘制边框)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (HexCoord coord : worldMap.getTiles().keySet()) {
            if (isInsideViewport(coord, camera)) {
                // 基础边框颜色，使用较淡的灰色增强在黑色背景下的可见度
                drawHexagon(coord, new Color(0.4f, 0.4f, 0.4f, 1f), false);
            }
        }

        // 2. 高亮当前悬停的瓦片
        if (highlightedCoord != null && worldMap.getTiles().containsKey(highlightedCoord)) {
            drawHexagon(highlightedCoord, Color.CYAN, false);
        }
        shapeRenderer.end();
    }

    /**
     * 视口剔除逻辑 (T046).
     */
    private boolean isInsideViewport(HexCoord coord, Camera camera) {
        Vector2 pos = hexToWorld(coord);
        // 简单的包围球检查，半径取 hexRadius 的 1.2 倍以确保安全余量
        return camera.frustum.boundsInFrustum(pos.x, pos.y, 0, hexRadius * 1.2f, hexRadius * 1.2f, 0);
    }

    private Color getColorForType(String typeId) {
        if (typeId == null) {
            return Color.BLACK;
        }
        return switch (typeId) {
            case "galaxy" ->
                new Color(0.4f, 0.4f, 0.8f, 1f); // 调亮深蓝色
            case "nebula" ->
                new Color(0.8f, 0.4f, 0.8f, 1f); // 调亮紫色
            case "deep_space" ->
                new Color(0.2f, 0.2f, 0.3f, 1f); // 调亮极深蓝
            default ->
                Color.GRAY;
        };
    }

    /**
     * 设置投影矩阵，通常由 WorldScreen 传递摄像机的 combined 矩阵。
     */
    public void setProjectionMatrix(com.badlogic.gdx.math.Matrix4 matrix) {
        shapeRenderer.setProjectionMatrix(matrix);
    }

    /**
     * 将立方体坐标转换为屏幕/世界空间坐标（Pointy-top 布局）。
     */
    public Vector2 hexToWorld(HexCoord coord) {
        // 使用 getX (q) 和 getZ (r) 映射到 2D 坐标。
        float x = hexRadius * (float) (Math.sqrt(3) * coord.getX() + Math.sqrt(3) / 2f * coord.getZ());
        float y = hexRadius * (3f / 2f * coord.getZ());
        // 修正：不再翻转 Y 轴，保持正 R 向上的习惯，解决坐标系偏差问题
        return new Vector2(x, y);
    }

    private void drawHexagon(HexCoord coord, Color color, boolean filled) {
        Vector2 center = hexToWorld(coord);
        float[] vertices = new float[12];

        for (int i = 0; i < 6; i++) {
            double angleDeg = 60 * i - 30; // Pointy top start angle
            double angleRad = Math.toRadians(angleDeg);
            vertices[i * 2] = center.x + (float) (hexRadius * Math.cos(angleRad));
            vertices[i * 2 + 1] = center.y + (float) (hexRadius * Math.sin(angleRad));
        }

        shapeRenderer.setColor(color);
        if (filled) {
            shapeRenderer.triangle(vertices[0], vertices[1], vertices[2], vertices[3], vertices[4], vertices[5]);
            shapeRenderer.triangle(vertices[0], vertices[1], vertices[4], vertices[5], vertices[6], vertices[7]);
            shapeRenderer.triangle(vertices[0], vertices[1], vertices[6], vertices[7], vertices[8], vertices[9]);
            shapeRenderer.triangle(vertices[0], vertices[1], vertices[8], vertices[9], vertices[10], vertices[11]);
        } else {
            shapeRenderer.polygon(vertices);
        }
    }

    public void dispose() {
        shapeRenderer.dispose();
    }

    public float getHexRadius() {
        return hexRadius;
    }

    public void setHexRadius(float hexRadius) {
        this.hexRadius = hexRadius;
    }
}
