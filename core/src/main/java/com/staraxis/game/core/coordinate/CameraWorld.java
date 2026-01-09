package com.staraxis.game.core.coordinate;

/**
 * 摄像机世界中心点状态 (CameraWorld).
 * <p>
 * 该类仅保存「摄像机在世界坐标系中的真实中心」——使用公里 (km) 作为内部单位，
 * 不受渲染层缩放 (kmPerPixel) 影响。
 * <p>
 * 设计目标：与正交相机的位置彻底解耦，实现任意缩放下世界坐标不漂移。
 * 之后所有 world→pixel 的换算统一使用：
 * <pre>
 *   float px = (float) ((worldKm - cameraWorld.getXKm()) / kmPerPixel);
 * </pre>
 */
public final class CameraWorld {

    /** 摄像机中心 X (km) */
    private double xKm;
    /** 摄像机中心 Y (km) */
    private double yKm;

    public CameraWorld() {
        this(0.0, 0.0);
    }

    public CameraWorld(double xKm, double yKm) {
        this.xKm = xKm;
        this.yKm = yKm;
    }

    // ----------------- 基本存取 -----------------
    public double getXKm() {
        return xKm;
    }

    public double getYKm() {
        return yKm;
    }

    public void set(double xKm, double yKm) {
        this.xKm = xKm;
        this.yKm = yKm;
    }

    /**
     * 按 km 偏移摄像机中心。
     */
    public void add(double dxKm, double dyKm) {
        this.xKm += dxKm;
        this.yKm += dyKm;
    }

    @Override
    public String toString() {
        return String.format("CameraWorld{xKm=%.3f, yKm=%.3f}", xKm, yKm);
    }
}
