package com.staraxis.game.client.ui.view.debug;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.staraxis.game.client.world.SectorModel;
import com.staraxis.game.client.world.UniverseModel;
import com.staraxis.game.shared.util.UnitConverter;

/**
 * F3 世界网格渲染（XY 平面），用于验证坐标/比例尺接入渲染。
 *
 * 规则（Spec Clarifications）：
 * - 网格位于 XY 平面
 * - 以世界原点对齐（线落在 step 的整数倍）
 * - 网格间距随比例尺变化，目标屏幕相邻网格线约 100px（允许使用“漂亮数”步长）
 * - 绘制范围自适应：覆盖可见范围 + 1.2x 余量
 */
public final class WorldGridRenderer {

    private final ShapeRenderer shapeRenderer;
    private boolean visible;

    public WorldGridRenderer() {
        this.shapeRenderer = new ShapeRenderer();
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(OrthographicCamera camera, double kmPerPixel, UniverseModel universe) {
        if (!visible) {
            return;
        }

        // 1. 渲染背景网格
        renderGrid(camera, kmPerPixel);

        // 2. 渲染星区标记
        if (universe != null) {
            renderSectors(camera, universe);
        }
    }

    private void renderGrid(OrthographicCamera camera, double kmPerPixel) {
        double targetStepKm = 100.0 * kmPerPixel;
        double stepKm = chooseNiceStep(targetStepKm);

        double viewWidthWorld = camera.viewportWidth * camera.zoom;
        double viewHeightWorld = camera.viewportHeight * camera.zoom;

        double halfW = viewWidthWorld * 0.5 * 1.2;
        double halfH = viewHeightWorld * 0.5 * 1.2;

        double camX = camera.position.x;
        double camY = camera.position.y;

        double minX = camX - halfW;
        double maxX = camX + halfW;
        double minY = camY - halfH;
        double maxY = camY + halfH;

        long startXIdx = (long) Math.floor(minX / stepKm);
        long endXIdx = (long) Math.ceil(maxX / stepKm);
        long startYIdx = (long) Math.floor(minY / stepKm);
        long endYIdx = (long) Math.ceil(maxY / stepKm);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(new Color(0.35f, 0.35f, 0.35f, 0.6f));

        for (long xi = startXIdx; xi <= endXIdx; xi++) {
            float x = (float) (xi * stepKm);
            shapeRenderer.line(x, (float) minY, x, (float) maxY);
        }

        for (long yi = startYIdx; yi <= endYIdx; yi++) {
            float y = (float) (yi * stepKm);
            shapeRenderer.line((float) minX, y, (float) maxX, y);
        }

        shapeRenderer.end();
    }

    private void renderSectors(OrthographicCamera camera, UniverseModel universe) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (SectorModel sector : universe.getSectors().values()) {
            Color color = switch (sector.getSectorType()) {
                case "star-system" -> Color.YELLOW;
                case "nebula" -> Color.PURPLE;
                case "deep_space" -> Color.DARK_GRAY;
                default -> Color.WHITE;
            };
            shapeRenderer.setColor(color);

            // 将光年坐标转换为公里，以匹配渲染单位
            double worldXKm = UnitConverter.lightYearsToKm(sector.getWorldPositionXLy());
            double worldYKm = UnitConverter.lightYearsToKm(sector.getWorldPositionYLy());

            // 渲染单位与相机缩放匹配
            double kmPerPixel = camera.zoom;
            if (!(kmPerPixel > 0) || Double.isNaN(kmPerPixel) || Double.isInfinite(kmPerPixel)) {
                kmPerPixel = 1.0;
            }

            float renderX = (float) (worldXKm / kmPerPixel);
            float renderY = (float) (worldYKm / kmPerPixel);

            float radius = 2.5f; // 像素半径，固定大小以便观察
            shapeRenderer.circle(renderX, renderY, radius);
        }

        shapeRenderer.end();
    }

    private static double chooseNiceStep(double targetStep) {
        if (!(targetStep > 0) || Double.isNaN(targetStep) || Double.isInfinite(targetStep)) {
            return 1.0;
        }

        double exponent = Math.floor(Math.log10(targetStep));
        double base = Math.pow(10, exponent);
        double fraction = targetStep / base;

        double nice;
        if (fraction <= 1.0) {
            nice = 1.0;
        } else if (fraction <= 2.0) {
            nice = 2.0;
        } else if (fraction <= 5.0) {
            nice = 5.0;
        } else {
            nice = 10.0;
        }
        return nice * base;
    }

    public void dispose() {
        shapeRenderer.dispose();
    }
}
