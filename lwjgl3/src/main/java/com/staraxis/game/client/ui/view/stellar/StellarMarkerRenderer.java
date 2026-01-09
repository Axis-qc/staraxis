package com.staraxis.game.client.ui.view.stellar;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.staraxis.game.client.world.SectorModel;
import com.staraxis.game.client.world.UniverseModel;
import com.staraxis.game.shared.net.worldgen.snapshot.PlanetSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.StarSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.StarSystemSnapshot;
import com.staraxis.game.shared.util.UnitConverter;
import com.staraxis.game.shared.world.astronomical.SectorSizeDefinition;

/**
 * 恒星/行星标记渲染器（StellarMarkerRenderer）。
 *
 * 作用：在六边形网格之上以 2D 方式绘制恒星/行星标记。
 * 依赖：使用 SectorModel 的物理世界坐标（光年），通过 UnitConverter 转换为渲染单位。
 * 对外接口：render/setProjectionMatrix/dispose。
 */
public class StellarMarkerRenderer {

    private final com.staraxis.game.core.coordinate.CameraWorld camWorld;

    private static final float PLANET_DETAIL_ZOOM_THRESHOLD = 0.65f; // 行星细节阈值（越小越近）
    private static final float STAR_DETAIL_ZOOM_THRESHOLD = 1.00f; // 恒星细节阈值（越小越近）

    private static final float SYSTEM_DOT_RADIUS = 10.0f;
    private static final float STAR_ORBIT_RADIUS = 24.0f;
    private static final float STAR_RADIUS = 12.0f;
    private static final float PLANET_ORBIT_RADIUS = 32.0f;
    private static final float PLANET_RADIUS = 6.0f;

    private final ShapeRenderer shapeRenderer;

    /** 在银河/光年级缩放时不再绘制恒星/行星标记，避免无意义的超大圆形导致渲染异常。 */
    private static final float MAX_EFFECTIVE_ZOOM = 5_000f;

    public StellarMarkerRenderer(com.staraxis.game.core.coordinate.CameraWorld camWorld) {
        this.shapeRenderer = new ShapeRenderer();
        this.camWorld = camWorld;
    }

    public void setProjectionMatrix(com.badlogic.gdx.math.Matrix4 matrix) {
        shapeRenderer.setProjectionMatrix(matrix);
    }

    public void render(UniverseModel universe, float zoom, Camera camera) {
        if (camWorld == null) return;
        if (universe == null) {
            return;
        }

        // 光年级大地图：只看星区/网格，不再画恒星/行星标记，直接跳过。
        if (zoom > MAX_EFFECTIVE_ZOOM) {
            return;
        }

        boolean starDetailEnabled = zoom <= STAR_DETAIL_ZOOM_THRESHOLD;
        boolean planetDetailEnabled = zoom <= PLANET_DETAIL_ZOOM_THRESHOLD;

        // 获取星区半径（光年），用于视锥体剔除
        double sectorRadiusLy = SectorSizeDefinition.DEFAULT_SECTOR_RADIUS_LY;
        double sectorRadiusKm = UnitConverter.lightYearsToKm(sectorRadiusLy);
        
        // 计算 kmPerPixel（渲染单位转换）
        double kmPerPixel = zoom;
        if (!(kmPerPixel > 0) || Double.isNaN(kmPerPixel) || Double.isInfinite(kmPerPixel)) {
            kmPerPixel = 1.0;
        }

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (SectorModel sector : universe.getSectors().values()) {
            StarSystemSnapshot system = sector.getStarSystem();
            if (system == null || system.getStars() == null || system.getStars().isEmpty()) {
                continue;
            }

            // 从 SectorModel 获取物理世界坐标（光年），转换为公里
            double worldXKm = UnitConverter.lightYearsToKm(sector.getWorldPositionXLy());
            double worldYKm = UnitConverter.lightYearsToKm(sector.getWorldPositionYLy());
            
            // 转换为渲染坐标 (CameraWorld)
            float centerX = (float) ((worldXKm - camWorld.getXKm()) / kmPerPixel);
            float centerY = (float) ((worldYKm - camWorld.getYKm()) / kmPerPixel);
            
            // 视锥体剔除：使用星区物理半径（公里）转换为渲染单位
            float renderRadius = (float) (sectorRadiusKm / kmPerPixel);
            if (!camera.frustum.boundsInFrustum(centerX, centerY, 0, renderRadius, renderRadius, 0)) {
                continue;
            }

            if (!starDetailEnabled) {
                // 远景：只画一个系统点
                shapeRenderer.setColor(new Color(1f, 0.95f, 0.7f, 1f));
                shapeRenderer.circle(centerX, centerY, SYSTEM_DOT_RADIUS * zoom);
                continue;
            }

            // 近景：画恒星点（最多 3 颗）
            float starRadius = STAR_RADIUS * zoom;
            float orbitRadius = STAR_ORBIT_RADIUS * zoom;

            for (int i = 0; i < system.getStars().size(); i++) {
                float angle = (float) (Math.PI * 2.0 * i / Math.max(1, system.getStars().size()));
                float sx = centerX + (float) Math.cos(angle) * orbitRadius;
                float sy = centerY + (float) Math.sin(angle) * orbitRadius;

                shapeRenderer.setColor(new Color(1f, 0.95f, 0.7f, 1f));
                shapeRenderer.circle(sx, sy, starRadius);

                if (planetDetailEnabled) {
                    // 更近：画行星点（只画数量，不展示类型；避免遮挡网格）
                    StarSnapshot star = system.getStars().get(i);
                    int maxPlanetsToDraw = star.getPlanets() != null ? Math.min(6, star.getPlanets().size()) : 0;
                    float planetOrbit = PLANET_ORBIT_RADIUS * zoom;
                    float planetRadius = PLANET_RADIUS * zoom;

                    for (int p = 0; p < maxPlanetsToDraw; p++) {
                        PlanetSnapshot planet = star.getPlanets().get(p);
                        Integer orbitIndexVal = planet.getOrbitIndex();
                        int orbitIndex = orbitIndexVal != null ? orbitIndexVal : p;
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
