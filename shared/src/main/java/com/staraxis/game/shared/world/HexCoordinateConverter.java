package com.staraxis.game.shared.world;

import com.staraxis.game.shared.world.astronomical.SectorSizeDefinition;

/**
 * 六边形坐标转换器，负责六边形网格坐标与物理世界坐标之间的转换。
 * 
 * 提供的接口：
 * - 六边形坐标 <-> 物理世界坐标（光年/AU）
 * - 计算六边形中心点坐标
 * - 计算两个六边形中心点之间的距离
 */
public class HexCoordinateConverter {
    
    private final SectorSizeDefinition sectorSize;
    
    public HexCoordinateConverter() {
        this(new SectorSizeDefinition());
    }
    
    public HexCoordinateConverter(SectorSizeDefinition sectorSize) {
        if (sectorSize == null) {
            throw new IllegalArgumentException("SectorSizeDefinition 不能为 null");
        }
        this.sectorSize = sectorSize;
    }
    
    /**
     * 获取星区尺寸定义
     */
    public SectorSizeDefinition getSectorSize() {
        return sectorSize;
    }
    
    // ===== 坐标转换 =====
    
    /**
     * 将六边形坐标转换为世界坐标（光年）
     * 
     * @param hexCoord 六边形坐标
     * @return 世界坐标 [x, y]（光年）
     */
    public double[] hexToWorld(HexCoord hexCoord) {
        return sectorSize.hexToWorld(hexCoord);
    }
    
    /**
     * 将世界坐标（光年）转换为最近的六边形坐标
     * 
     * @param worldX 世界X坐标（光年）
     * @param worldY 世界Y坐标（光年）
     * @return 最近的六边形坐标
     */
    public HexCoord worldToHex(double worldX, double worldY) {
        return sectorSize.worldToHex(worldX, worldY);
    }
    
    /**
     * 获取六边形中心点的世界坐标（光年）
     * 
     * @param hexCoord 六边形坐标
     * @return 中心点坐标 [x, y]（光年）
     */
    public double[] getHexCenter(HexCoord hexCoord) {
        return hexToWorld(hexCoord);
    }
    
    /**
     * 获取六边形中心点的世界坐标（AU）
     * 
     * @param hexCoord 六边形坐标
     * @return 中心点坐标 [x, y]（AU）
     */
    public double[] getHexCenterInAU(HexCoord hexCoord) {
        double[] center = getHexCenter(hexCoord);
        double auPerLightYear = SectorSizeDefinition.LIGHT_YEAR_IN_AU;
        return new double[]{
            center[0] * auPerLightYear,
            center[1] * auPerLightYear
        };
    }
    
    /**
     * 计算两个六边形中心点之间的距离（光年）
     * 
     * @param coord1 第一个六边形坐标
     * @param coord2 第二个六边形坐标
     * @return 两点之间的距离（光年）
     */
    public double distanceBetween(HexCoord coord1, HexCoord coord2) {
        double[] pos1 = hexToWorld(coord1);
        double[] pos2 = hexToWorld(coord2);
        double dx = pos2[0] - pos1[0];
        double dy = pos2[1] - pos1[1];
        return Math.sqrt(dx * dx + dy * dy);
    }
    
    /**
     * 计算两个六边形中心点之间的距离（AU）
     * 
     * @param coord1 第一个六边形坐标
     * @param coord2 第二个六边形坐标
     * @return 两点之间的距离（AU）
     */
    public double distanceBetweenInAU(HexCoord coord1, HexCoord coord2) {
        return distanceBetween(coord1, coord2) * SectorSizeDefinition.LIGHT_YEAR_IN_AU;
    }
    
    /**
     * 获取六边形的顶点坐标（世界坐标，光年）
     * 
     * @param hexCoord 六边形坐标
     * @return 包含6个顶点坐标的数组，每个顶点为 [x, y]
     */
    public double[][] getHexVertices(HexCoord hexCoord) {
        double[] center = hexToWorld(hexCoord);
        double radius = sectorSize.getRadiusInWorldUnits();
        
        // 六边形的6个顶点（从正右开始，逆时针方向）
        double[][] vertices = new double[6][2];
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 3.0 * i; // 60度一个顶点
            vertices[i][0] = center[0] + radius * Math.cos(angle);
            vertices[i][1] = center[1] + radius * Math.sin(angle);
        }
        return vertices;
    }
    
    /**
     * 检查一个世界坐标点是否在六边形内
     * 
     * @param worldX 世界X坐标（光年）
     * @param worldY 世界Y坐标（光年）
     * @param hexCoord 六边形坐标
     * @return 如果点在六边形内返回true，否则返回false
     */
    public boolean isPointInHex(double worldX, double worldY, HexCoord hexCoord) {
        double[] center = hexToWorld(hexCoord);
        double dx = worldX - center[0];
        double dy = worldY - center[1];
        double distanceSq = dx * dx + dy * dy;
        double radius = sectorSize.getRadiusInWorldUnits();
        
        // 先检查是否在外接圆外
        if (distanceSq > radius * radius) {
            return false;
        }
        
        // 检查是否在内切圆内（快速通过）
        double inradius = radius * Math.sqrt(3) / 2.0; // 内切圆半径
        if (distanceSq <= inradius * inradius) {
            return true;
        }
        
        // 精确检查六边形边界
        double angle = Math.atan2(dy, dx);
        // 将角度转换到[0, 2π]范围
        if (angle < 0) angle += 2 * Math.PI;
        // 计算当前角度所在的60度扇区
        int sector = (int) (angle / (Math.PI / 3.0));
        // 计算扇区边界角度
        double sectorAngle1 = sector * Math.PI / 3.0;
        double sectorAngle2 = (sector + 1) * Math.PI / 3.0;
        // 计算点到两条边的距离
        double dist1 = Math.abs(dx * Math.sin(sectorAngle1) - dy * Math.cos(sectorAngle1));
        double dist2 = Math.abs(dx * Math.sin(sectorAngle2) - dy * Math.cos(sectorAngle2));
        // 点到最近边的距离应小于等于六边形边长的一半
        double sideLength = radius;
        return dist1 <= sideLength / 2.0 && dist2 <= sideLength / 2.0;
    }
}