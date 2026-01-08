package com.staraxis.game.core.coordinate;

/**
 * 比例尺系统：根据摄像机 zoom 计算当前比例尺（km/px）。
 *
 * 规范（Spec FR-4 / Clarifications）：
 * - zoom=1.0 为最大放大（最近），此时 1px = 1km
 * - 线性关系：kmPerPixel = zoom * 1km
 * - zoom 越大，看得越远（每像素代表更多 km）
 */
public final class ScaleSystem {

    /** zoom=1 时的基准比例尺（km/px）。 */
    public static final double BASE_KM_PER_PIXEL = 1.0;

    private double zoom = 1.0;

    public ScaleSystem() {
    }

    public ScaleSystem(double zoom) {
        setZoom(zoom);
    }

    public double getZoom() {
        return zoom;
    }

    public void setZoom(double zoom) {
        if (Double.isNaN(zoom) || Double.isInfinite(zoom) || zoom <= 0) {
            throw new IllegalArgumentException("zoom must be finite and > 0");
        }
        this.zoom = zoom;
    }

    /**
     * @return 当前比例尺（km/px）
     */
    public double getKmPerPixel() {
        return zoom * BASE_KM_PER_PIXEL;
    }
}
