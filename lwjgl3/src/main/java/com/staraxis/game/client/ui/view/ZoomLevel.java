package com.staraxis.game.client.ui.view;

/**
 * 语义缩放层级 (Semantic Zoom Tiers). 对应
 * specs/005-hex-world-gen/contracts/zoom-tiers.md
 */
public enum ZoomLevel {
    MICRO(1.2f, Float.MAX_VALUE),
    NORMAL(0.5f, 1.2f),
    MACRO(0.0f, 0.5f);

    private final float min;
    private final float max;

    ZoomLevel(float min, float max) {
        this.min = min;
        this.max = max;
    }

    public static ZoomLevel fromZoom(float zoom) {
        for (ZoomLevel level : values()) {
            if (zoom >= level.min && zoom < level.max) {
                return level;
            }
        }
        return NORMAL;
    }

    public boolean isAtLeast(ZoomLevel other) {
        return this.ordinal() <= other.ordinal();
    }
}
