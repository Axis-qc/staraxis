package com.staraxis.game.client.ui.view;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.HexTile;
import com.staraxis.game.shared.world.WorldMap;

import java.util.Collection;

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

        // 1. 渲染填充颜色 (瓦片类型)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (HexTile tile : worldMap.getTiles().values()) {
            if (isInsideViewport(tile.getCoord(), camera)) {
                drawHexagon(tile.getCoord(), getColorForType(tile.getTypeId()), true);
            }
        }
        shapeRenderer.end();

        // 2. 渲染边框与高亮 (根据 LOD 决定是否渲染细边框)
        if (level.isAtLeast(ZoomLevel.NORMAL)) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(Color.DARK_GRAY);
            for (HexCoord coord : worldMap.getTiles().keySet()) {
                if (isInsideViewport(coord, camera)) {
                    drawHexagon(coord, Color.DARK_GRAY, false);
                }
            }

            if (highlightedCoord != null && worldMap.getTiles().containsKey(highlightedCoord)) {
                shapeRenderer.setColor(Color.CYAN);
                drawHexagon(highlightedCoord, Color.CYAN, false);
            }
            shapeRenderer.end();
        }
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
                new Color(0.2f, 0.2f, 0.5f, 1f); // 深蓝色
            case "nebula" ->
                new Color(0.5f, 0.2f, 0.5f, 1f); // 紫色
            case "deep_space" ->
                new Color(0.05f, 0.05f, 0.1f, 1f); // 极深蓝
            default ->
                Color.BLACK;
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
        float x = hexRadius * (float) (Math.sqrt(3) * coord.getX() + Math.sqrt(3) / 2f * coord.getY());
        float y = hexRadius * (3f / 2f * coord.getY());
        return new Vector2(x, -y); // Y轴翻转以匹配常见的 Top-down 习惯
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
