package com.staraxis.game.client.ui.view.debug;

import com.staraxis.game.core.coordinate.CoordinateService;
import com.staraxis.game.core.coordinate.ScaleSystem;
import com.staraxis.game.core.coordinate.WorldCoordinate;

/**
 * 调试系统（client 表现层）：持有 F3 调试开关状态，并把 core 的数据转换为 UI/渲染可消费的快照。
 *
 * 宪章约束：
 * - core 不依赖 UI；因此 debugEnabled 状态仅存在于 client。
 */
public final class DebugSystem {

    private boolean enabled;

    private final CoordinateService coordinateService;

    // 暂时用“相机中心点世界坐标”作为输入；接入实际游戏相机后替换。
    private WorldCoordinate cameraWorld = new WorldCoordinate(0, 0, 0, 0, 0, 0);

    public DebugSystem(CoordinateService coordinateService) {
        this.coordinateService = coordinateService;
    }

    public void toggle() {
        enabled = !enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setCameraWorld(WorldCoordinate cameraWorld) {
        this.cameraWorld = cameraWorld;
    }

    public DebugOverlayState snapshot(double cameraZoom) {
        ScaleSystem scale = coordinateService.getScaleSystem();
        scale.setZoom(cameraZoom);
        double kmPerPixel = scale.getKmPerPixel();
        String scaleText = ScaleTextUtil.formatScale(kmPerPixel);
        return new DebugOverlayState(enabled, cameraWorld, cameraZoom, kmPerPixel, scaleText);
    }
}
