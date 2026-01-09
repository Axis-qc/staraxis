/**
 * 文件作用：定义在单个六边形星区内的局部2D坐标。
 * 使用的接口：
 *  - com.staraxis.game.shared.world.HexCoord
 *  - com.staraxis.game.shared.model.Vector2d
 * 提供的接口：
 *  - SectorPosition(...): 构造函数
 *  - distanceTo(SectorPosition other): 计算同一星区内两点间的距离
 */
package com.staraxis.game.shared.coordinate;

import com.staraxis.game.shared.model.Vector2d;
import com.staraxis.game.shared.world.HexCoord;

/**
 * 恒星系（星区）坐标（Sector Position）。
 * <p>
 * 用于表示在单个六边形星区内的局部位置。每个星区的中心即为该坐标系的原点 (0,0)。
 * 这种设计有助于简化星系内部的物理和逻辑计算，并避免浮点数精度问题。
 * 本坐标系为平面2D坐标系。
 *
 * @param sectorCoord 所在的六边形星区的坐标（使用立方体坐标）。
 * @param localOffset 在星区内的局部偏移，单位为AU。
 */
public final class SectorPosition {

    private final HexCoord sectorCoord;
    private final Vector2d localOffset;

    public SectorPosition(HexCoord sectorCoord, Vector2d localOffset) {
        this.sectorCoord = sectorCoord;
        this.localOffset = localOffset;
    }

    public HexCoord getSectorCoord() {
        return sectorCoord;
    }

    public Vector2d getLocalOffset() {
        return localOffset;
    }

    /**
     * 计算与同一星区内另一个坐标点之间的欧几里得距离。
     *
     * @param other 同一个星区内的另一个坐标点。
     * @return 两点间的距离（单位：AU）。
     * @throws IllegalArgumentException 如果两个坐标不属于同一个星区。
     */
    public double distanceTo(SectorPosition other) {
        if (!this.sectorCoord.equals(other.sectorCoord)) {
            throw new IllegalArgumentException("Cannot measure distance across different sectors. Convert to WorldPosition first.");
        }
        double dx = this.localOffset.x - other.localOffset.x;
        double dy = this.localOffset.y - other.localOffset.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public String toString() {
        return "SectorPosition{" +
                "sectorCoord=" + sectorCoord +
                ", localOffset=" + localOffset +
                '}';
    }
}
