package com.staraxis.game.client.ui.view.debug;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.staraxis.game.core.coordinate.WorldCoordinate;

/**
 * F3 调试 UI：显示相机中心点坐标、zoom、比例尺（1px = N unit）。
 *
 * 实现：
 * - 使用 Scene2D UI 的 Table + Label，确保布局稳定且在 UI 顶层。
 */
public final class DebugOverlayController {

    private final Table table;
    private final Label label;

    public DebugOverlayController(Skin skin) {
        table = new Table();
        table.setFillParent(true);
        table.top().left();

        label = new Label("", skin);
        label.setAlignment(0); // left
        // 限制宽度，避免左侧文本被裁切/溢出屏幕
        table.add(label).pad(5).width(Math.min(520f, Gdx.graphics.getWidth() - 20f)).left();
    }

    public Table getActor() {
        return table;
    }

    public void setVisible(boolean visible) {
        table.setVisible(visible);
    }

    public void update(WorldCoordinate cameraWorld, double zoom, double kmPerPixel, String scaleText) {
        if (!table.isVisible()) {
            return;
        }

        StringBuilder sb = new StringBuilder(256);
        sb.append("F3 Debug\n");

        if (cameraWorld != null) {
            sb.append(String.format("camera.grid=(%d,%d,%d)\n",
                    cameraWorld.getGridX(), cameraWorld.getGridY(), cameraWorld.getGridZ()));
            sb.append(String.format("camera.offsetKm=(%.3f,%.3f,%.3f)\n",
                    cameraWorld.getOffsetXKm(), cameraWorld.getOffsetYKm(), cameraWorld.getOffsetZKm()));
        }

        sb.append(String.format("zoom=%.3f\n", zoom));
        sb.append(String.format("kmPerPixel=%.6f\n", kmPerPixel));
        sb.append(scaleText);

        label.setText(sb.toString());
    }
}
