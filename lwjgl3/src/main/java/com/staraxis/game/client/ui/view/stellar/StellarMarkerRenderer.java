package com.staraxis.game.client.ui.view.stellar;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.staraxis.game.client.ui.view.HexGridRenderer;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.HexTile;
import com.staraxis.game.shared.world.WorldMap;
import com.staraxis.game.shared.world.stellar.Planet;
import com.staraxis.game.shared.world.stellar.Star;
import com.staraxis.game.shared.world.stellar.StarSystem;

/**
 * 恒星/行星标记渲染器（StellarMarkerRenderer）。
 *
 * 作用（Purpose）：在六边形网格之上以 2D 方式绘制恒星/行星的最小可用标记（US3 MVP）。 依赖（Dependencies）：复用
 * HexGridRenderer 的坐标换算；使用 ShapeRenderer 绘制。 对外接口（Public
 * API）：render/setProjectionMatrix/dispose。
 */
public class StellarMarkerRenderer {

    private static final float PLANET_DETAIL_ZOOM_THRESHOLD = 0.65f; // 行星细节阈值（越小越近）
    private static final float STAR_DETAIL_ZOOM_THRESHOLD = 1.00f; // 恒星细节阈值（越小越近）

    private static final float SYSTEM_DOT_RADIUS = 10.0f;
    private static final float STAR_ORBIT_RADIUS = 24.0f;
    private static final float STAR_RADIUS = 12.0f;
    private static final float PLANET_ORBIT_RADIUS = 32.0f;
    private static final float PLANET_RADIUS = 6.0f;

    private final ShapeRenderer shapeRenderer;
    private final HexGridRenderer gridRenderer;

    public StellarMarkerRenderer(HexGridRenderer gridRenderer) {
        this.shapeRenderer = new ShapeRenderer();
        this.gridRenderer = gridRenderer;
    }

    public void setProjectionMatrix(com.badlogic.gdx.math.Matrix4 matrix) {
        shapeRenderer.setProjectionMatrix(matrix);
    }

    public void render(WorldMap worldMap, float zoom, Camera camera) {
        if (worldMap == null) {
            return;
        }

        boolean starDetailEnabled = zoom <= STAR_DETAIL_ZOOM_THRESHOLD;
        boolean planetDetailEnabled = zoom <= PLANET_DETAIL_ZOOM_THRESHOLD;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (HexCoord coord : worldMap.getTiles().keySet()) {
            HexTile tile = worldMap.getTile(coord);
            StarSystem system = tile.getStarSystem();
            if (system == null) {
                continue;
            }

            Vector2 center = gridRenderer.hexToWorld(coord);
            if (!camera.frustum.boundsInFrustum(center.x, center.y, 0, gridRenderer.getHexRadius(), gridRenderer.getHexRadius(), 0)) {
                continue;
            }

            if (!starDetailEnabled) {
                // 远景：只画一个系统点
                shapeRenderer.setColor(new Color(1f, 0.95f, 0.7f, 1f));
                shapeRenderer.circle(center.x, center.y, SYSTEM_DOT_RADIUS * zoom);
                continue;
            }

            // 近景：画恒星点（最多 3 颗）
            float starRadius = STAR_RADIUS * zoom;
            float orbitRadius = STAR_ORBIT_RADIUS * zoom;

            for (int i = 0; i < system.getStars().size(); i++) {
                float angle = (float) (Math.PI * 2.0 * i / Math.max(1, system.getStars().size()));
                float sx = center.x + (float) Math.cos(angle) * orbitRadius;
                float sy = center.y + (float) Math.sin(angle) * orbitRadius;

                shapeRenderer.setColor(new Color(1f, 0.95f, 0.7f, 1f));
                shapeRenderer.circle(sx, sy, starRadius);

                if (planetDetailEnabled) {
                    // 更近：画行星点（只画数量，不展示类型；避免遮挡网格）
                    Star star = system.getStars().get(i);
                    int maxPlanetsToDraw = Math.min(6, star.getPlanets().size());
                    float planetOrbit = PLANET_ORBIT_RADIUS * zoom;
                    float planetRadius = PLANET_RADIUS * zoom;

                    for (int p = 0; p < maxPlanetsToDraw; p++) {
                        Planet planet = star.getPlanets().get(p);
                        int orbitIndex = planet.getOrbitIndex() != null ? planet.getOrbitIndex() : p;
                        float pa = (float) (Math.PI * 2.0 * (orbitIndex % 12) / 12.0);
                        float px = sx + (float) Math.cos(pa) * planetOrbit;
                        float py = sy + (float) Math.sin(pa) * planetOrbit;
                        shapeRenderer.setColor(new Color(0.6f, 0.8f, 1f, 1f));
                        shapeRenderer.circle(px, py, planetRadius);
                    }
                }
            }
        }
        shapeRenderer.end();
    }

    public void dispose() {
        shapeRenderer.dispose();
    }
}
