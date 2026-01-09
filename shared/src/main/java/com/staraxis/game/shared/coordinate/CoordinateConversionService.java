/**
 * 文件作用：提供在 WorldPosition 和 SectorPosition 之间进行转换的服务。
 * 使用的接口：
 *  - WorldPosition, SectorPosition, Vector2d
 *  - com.staraxis.game.shared.world.HexCoord
 *  - com.staraxis.game.shared.world.astronomical.SectorSizeDefinition
 * 提供的接口：
 *  - worldToSector(WorldPosition worldPos)
 *  - sectorToWorld(SectorPosition sectorPos)
 */
package com.staraxis.game.shared.coordinate;

import com.staraxis.game.shared.model.Vector2d;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.astronomical.SectorSizeDefinition;

/**
 * 坐标转换服务。
 * <p>
 * 负责在宏观的 {@link WorldPosition} 和局部的 {@link SectorPosition} 之间进行精确转换。
 */
public final class CoordinateConversionService {

    private final SectorSizeDefinition sectorSize;

    public CoordinateConversionService(SectorSizeDefinition sectorSize) {
        this.sectorSize = sectorSize;
    }

    /**
     * 将星区坐标 (SectorPosition) 转换为世界坐标 (WorldPosition)。
     *
     * @param sectorPos 要转换的星区坐标。
     * @return 对应的世界坐标。
     */
    public WorldPosition sectorToWorld(SectorPosition sectorPos) {
        // 1. 获取六边形星区的中心点在世界空间中的绝对坐标（单位：光年）
        double[] sectorCenterLy = sectorSize.hexToWorld(sectorPos.getSectorCoord());

        // 2. 将中心点坐标从光年转换为AU
        double sectorCenterAuX = sectorCenterLy[0] * SectorSizeDefinition.LIGHT_YEAR_IN_AU;
        double sectorCenterAuY = sectorCenterLy[1] * SectorSizeDefinition.LIGHT_YEAR_IN_AU;

        // 3. 将星区内的局部偏移（单位：AU）加到中心点坐标上，得到最终的绝对AU坐标
        double absoluteAuX = sectorCenterAuX + sectorPos.getLocalOffset().x;
        double absoluteAuY = sectorCenterAuY + sectorPos.getLocalOffset().y;

        // 4. 将绝对AU坐标转换为分层的 WorldPosition
        long gridX = (long) Math.floor(absoluteAuX / WorldPosition.CELL_SIZE_AU);
        long gridY = (long) Math.floor(absoluteAuY / WorldPosition.CELL_SIZE_AU);

        double localOffsetX = absoluteAuX - gridX * WorldPosition.CELL_SIZE_AU;
        double localOffsetY = absoluteAuY - gridY * WorldPosition.CELL_SIZE_AU;

        return new WorldPosition(gridX, gridY, new Vector2d(localOffsetX, localOffsetY));
    }

    /**
     * 将世界坐标 (WorldPosition) 转换为星区坐标 (SectorPosition)。
     *
     * @param worldPos 要转换的世界坐标。
     * @return 对应的星区坐标。
     */
    public SectorPosition worldToSector(WorldPosition worldPos) {
        // 1. 将分层的世界坐标转换为绝对AU坐标
        Vector2d absoluteAu = worldPos.toAbsoluteAU();

        // 2. 将绝对AU坐标转换为光年，因为 worldToHex 方法需要光年单位
        double absoluteLyX = absoluteAu.x / SectorSizeDefinition.LIGHT_YEAR_IN_AU;
        double absoluteLyY = absoluteAu.y / SectorSizeDefinition.LIGHT_YEAR_IN_AU;

        // 3. 根据世界坐标（光年）计算出所在的六边形星区坐标
        HexCoord sectorCoord = sectorSize.worldToHex(absoluteLyX, absoluteLyY);

        // 4. 计算该星区的中心点在世界空间中的绝对坐标（单位：AU）
        double[] sectorCenterLy = sectorSize.hexToWorld(sectorCoord);
        double sectorCenterAuX = sectorCenterLy[0] * SectorSizeDefinition.LIGHT_YEAR_IN_AU;
        double sectorCenterAuY = sectorCenterLy[1] * SectorSizeDefinition.LIGHT_YEAR_IN_AU;

        // 5. 从绝对世界坐标中减去星区中心坐标，得到在星区内的局部偏移
        double localOffsetX = absoluteAu.x - sectorCenterAuX;
        double localOffsetY = absoluteAu.y - sectorCenterAuY;

        return new SectorPosition(sectorCoord, new Vector2d(localOffsetX, localOffsetY));
    }
}
