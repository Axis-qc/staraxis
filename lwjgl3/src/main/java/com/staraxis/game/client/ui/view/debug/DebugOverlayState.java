package com.staraxis.game.client.ui.view.debug;

import com.staraxis.game.core.coordinate.WorldCoordinate;

/**
 * F3 调试渲染所需的数据快照（UI/渲染层消费）。
 *
 * 说明：
 * - 按宪章要求：core 不依赖 UI；这里的数据结构位于 client 表现层。
 */
public record DebugOverlayState(
        boolean enabled,
        WorldCoordinate cameraWorld,
        double zoom,
        double kmPerPixel,
        String scaleText
) {
}
