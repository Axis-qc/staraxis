/**
 * 文件作用：根据相机 zoomFactor 计算 AU/pixel 的比例尺，实现《游戏大纲.md》中的缩放规则：
 *   - zoomFactor = 1 时，100px = 1 AU => 1px = 0.01 AU。
 *   - zoomFactor 越大，看得越远，1px 代表的 AU 越大。
 * 
 * 提供的接口：
 *   - setZoomFactor(double)
 *   - getZoomFactor()
 *   - getAuPerPixel()
 *   - getPixelsPerAu()
 */
package com.staraxis.game.shared.coordinate;

/**
 * AU 缩放系统 (Astronomical Unit Scale System)。
 */
public final class ZoomScaleSystem {

    /** zoomFactor=1 时的基准比例：1px = 0.01 AU。 */
    public static final double BASE_AU_PER_PIXEL = 0.01;

    private double zoomFactor = 1.0;

    public ZoomScaleSystem() {
    }

    public ZoomScaleSystem(double zoomFactor) {
        setZoomFactor(zoomFactor);
    }

    public double getZoomFactor() {
        return zoomFactor;
    }

    public void setZoomFactor(double zoomFactor) {
        if (Double.isNaN(zoomFactor) || Double.isInfinite(zoomFactor) || zoomFactor <= 0) {
            throw new IllegalArgumentException("zoomFactor 必须为有限正数");
        }
        this.zoomFactor = zoomFactor;
    }

    /**
     * 获取当前缩放下 1px 表示的 AU 数量。
     * 公式：distancePerPixelAU = 0.01 × zoomFactor
     */
    public double getAuPerPixel() {
        return BASE_AU_PER_PIXEL * zoomFactor;
    }

    /**
     * 获取当前缩放下 1 AU 对应的像素数。pixelsPerAu = 1 / auPerPixel。
     */
    public double getPixelsPerAu() {
        return 1.0 / getAuPerPixel();
    }
}
