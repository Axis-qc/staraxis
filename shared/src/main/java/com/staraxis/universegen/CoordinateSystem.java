package com.staraxis.universegen;

/**
 * 分层坐标实现：
 * 顶层以 64 位 sectorId 标识六边形星区，局部坐标使用 double 存储公里。
 */
public final class CoordinateSystem {

    private final long sectorId;   // 64-bit sector identifier (axial coords packed)
    private final double xKm;
    private final double yKm;
    private final double zKm;

    public CoordinateSystem(long sectorId, double xKm, double yKm, double zKm) {
        this.sectorId = sectorId;
        this.xKm = xKm;
        this.yKm = yKm;
        this.zKm = zKm;
    }

    public long getSectorId() { return sectorId; }
    public double getXKm() { return xKm; }
    public double getYKm() { return yKm; }
    public double getZKm() { return zKm; }

    /**
     * 计算局部坐标之间的欧几里得距离（公里）。
     */
    public double distanceTo(CoordinateSystem other) {
        if (this.sectorId != other.sectorId) {
            throw new IllegalArgumentException("Cannot measure distance across sectors – convert to galaxy space first");
        }
        double dx = xKm - other.xKm;
        double dy = yKm - other.yKm;
        double dz = zKm - other.zKm;
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    @Override
    public String toString() {
        return "[sector=" + sectorId + ", (" + xKm + "," + yKm + "," + zKm + ") km]";
    }
}
