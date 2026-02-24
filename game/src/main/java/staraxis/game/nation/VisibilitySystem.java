package staraxis.game.nation;

import staraxis.game.state.WorldState;
import staraxis.game.world.WorldSector;
import staraxis.game.world.Vec2d;
import staraxis.game.world.hex.SectorCoord;
import staraxis.game.entity.Entity;

import java.util.HashSet;
import java.util.Set;

/**
 * VisibilitySystem（可见性系统）
 *
 * 作用：计算每个国家在当前 tick 下的可见性状态，包括星区可见性、实体可见性等。
 *
 * 可见性模型（三级可见性）：
 * 1. 完全可见：实时状态，包括实体细节、位置、状态等
 * 2. 部分可见（仅轮廓）：仅知道存在和基本类型，无详细状态
 * 3. 不可见：完全隐藏，无任何信息
 *
 * 可见性影响因素：
 * 1. 星区控制：本国控制的星区完全可见
 * 2. 传感器范围：传感器范围内的星区部分可见
 * 3. 外交关系：同盟国家共享部分可见性，敌对国不可见
 * 4. 探索状态：已探索的星区保留记忆层（上次快照）
 * 5. 实体类型：某些实体（如恒星）对所有国家可见
 *
 * 注意事项：
 * - 可见性计算是权威模拟层的一部分，必须保证确定性
 * - 性能敏感：采用空间分区和增量更新优化
 * - 结果缓存：每 tick 计算一次，变化时触发事件
 */
public class VisibilitySystem {

    private final WorldState worldState;

    /**
     * 构造函数。
     *
     * @param worldState 世界状态引用
     */
    public VisibilitySystem(WorldState worldState) {
        this.worldState = worldState;
    }

    /**
     * 计算指定国家在当前 tick 下的可见星区。
     *
     * @param nationId 国家ID
     * @return 可见星区坐标集合（字符串表示）
     */
    public Set<String> computeVisibleSectors(String nationId) {
        NationState nationState = worldState.nationManager.getNationState(nationId);
        if (nationState == null || !nationState.isActive()) {
            return new HashSet<>();
        }

        Set<String> visibleSectors = new HashSet<>();

        // 1. 遍历所有星区，根据控制权判断可见性
        for (WorldSector sector : worldState.worldMap.getSectorsView()) {
            SectorCoord coord = sector.coord;
            String coordKey = coordToKey(coord);

            // 检查星区控制：如果星区归属于本国，则完全可见
            if (nationId.equals(sector.ownerNationId)) {
                visibleSectors.add(coordKey);
                continue;
            }

            // 2. 检查传感器范围：计算距离是否在传感器范围内
            if (isInSensorRange(nationState, sector)) {
                visibleSectors.add(coordKey);
                continue;
            }

            // 3. 检查外交共享：如果是同盟国且同盟国在该星区有单位，则部分可见
            if (hasAlliedPresence(nationId, sector)) {
                visibleSectors.add(coordKey);
                continue;
            }
        }

        return visibleSectors;
    }

    /**
     * 计算指定实体的对指定国家的可见性级别。
     *
     * @param entity   实体
     * @param nationId 国家ID
     * @return 可见性级别："FULL"（完全可见）、"PARTIAL"（部分可见）、"NONE"（不可见）
     */
    public String computeEntityVisibility(Entity entity, String nationId) {
        if (entity == null)
            return "NONE";

        // 特殊实体：恒星对所有国家完全可见（基础天文知识）
        if (entity.entityType == staraxis.game.entity.EntityType.STAR) {
            return "FULL";
        }

        // 实体所属国家与查询国家相同：完全可见
        if (nationId.equals(entity.ownerNationId)) {
            return "FULL";
        }

        NationState nationState = worldState.nationManager.getNationState(nationId);
        if (nationState == null)
            return "NONE";

        // 获取实体所在星区
        SectorCoord sectorCoord = entity.sectorCoord;
        if (sectorCoord == null)
            return "NONE";

        String coordKey = coordToKey(sectorCoord);

        // 检查星区是否当前可见
        if (nationState.isSectorVisible(coordKey)) {
            // 可见星区内的实体：部分可见（仅轮廓）
            return "PARTIAL";
        }

        // 检查星区是否已探索（记忆层）
        if (nationState.isSectorExplored(coordKey)) {
            // 已探索但当前不可见：返回部分可见（显示上次快照）
            return "PARTIAL";
        }

        return "NONE";
    }

    /**
     * 计算指定国家的“情报可见星区”（服务端权威口径）喵。
     *
     * 规则喵：
     * - 只要某星区内存在本国拥有实体（Entity.ownerNationId == nationId），该星区即为可见喵。
     * - 并扩展到该星区周边一圈六邻居星区（hex distance=1）作为情报可见星区喵。
     *
     * 说明喵：
     * - 返回 SectorCoord 集合，供 webnet 用于过滤“细节/私有数据”下发喵。
     * - 前端不参与该计算，避免伪造可见范围导致越权喵。
     *
     * @param nationId 国家ID
     * @return 情报可见星区坐标集合
     */
    public Set<SectorCoord> computeIntelVisibleSectorsForNation(String nationId) {
        Set<SectorCoord> result = new HashSet<>();
        if (nationId == null || nationId.isBlank()) {
            return result;
        }

        // 1) 找到所有本国拥有实体所在星区喵
        Set<SectorCoord> ownedSectors = new HashSet<>();
        for (Entity entity : worldState.entitiesById.values()) {
            if (entity == null) {
                continue;
            }
            if (!nationId.equals(entity.ownerNationId)) {
                continue;
            }
            if (entity.sectorCoord == null) {
                continue;
            }
            ownedSectors.add(entity.sectorCoord);
        }

        // 2) ownedSectors 本身可见喵
        result.addAll(ownedSectors);

        // 3) 扩展周边一圈六邻居喵
        for (SectorCoord c : ownedSectors) {
            if (c == null) {
                continue;
            }
            // axial 六邻居方向喵
            SectorCoord[] neighbors = new SectorCoord[] {
                    new SectorCoord(c.q() + 1, c.r()),
                    new SectorCoord(c.q() + 1, c.r() - 1),
                    new SectorCoord(c.q(), c.r() - 1),
                    new SectorCoord(c.q() - 1, c.r()),
                    new SectorCoord(c.q() - 1, c.r() + 1),
                    new SectorCoord(c.q(), c.r() + 1)
            };
            for (SectorCoord n : neighbors) {
                if (n == null) {
                    continue;
                }
                if (worldState.worldMap.getSector(n) != null) {
                    result.add(n);
                }
            }
        }

        return result;
    }

    /**
     * 更新指定国家的可见性状态（每 tick 调用）。
     *
     * @param nationId 国家ID
     */
    public void updateNationVisibility(String nationId) {
        NationState nationState = worldState.nationManager.getNationState(nationId);
        if (nationState == null)
            return;

        // 清除当前可见星区
        nationState.visibleSectorCoords.clear();

        // 计算新的可见星区
        Set<String> visibleSectors = computeVisibleSectors(nationId);
        for (String coordKey : visibleSectors) {
            nationState.addVisibleSector(coordKey);
            // 如果是首次发现，添加到探索记录
            if (!nationState.isSectorExplored(coordKey)) {
                nationState.addExploredSector(coordKey);
            }
        }
    }

    /**
     * 更新所有国家的可见性状态（每 tick 调用）。
     */
    public void updateAllNationsVisibility() {
        for (String nationId : worldState.nationManager.getAllNationIds()) {
            updateNationVisibility(nationId);
        }
    }

    /**
     * 检查星区是否在国家的传感器范围内。
     *
     * @param nationState 国家运行时状态
     * @param sector      星区
     * @return 如果在传感器范围内则返回 true
     */
    private boolean isInSensorRange(NationState nationState, WorldSector sector) {
        double sensorRangeGU = nationState.getSensorRangeGU();

        // 遍历所有本国实体，检查是否在传感器范围内
        for (Entity entity : worldState.entitiesById.values()) {
            if (entity == null || !nationState.nationId.equals(entity.ownerNationId)) {
                continue;
            }

            // 检查实体位置是否有效
            if (entity.posWorldGU == null || sector.centerWorldGU == null) {
                continue;
            }

            // 计算实体到星区中心的距离
            double distance = calculateDistance(entity.posWorldGU, sector.centerWorldGU);
            if (distance <= sensorRangeGU) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查星区内是否有同盟国单位。
     *
     * @param nationId 国家ID
     * @param sector   星区
     * @return 如果有同盟国单位则返回 true
     */
    private boolean hasAlliedPresence(String nationId, WorldSector sector) {
        NationState nationState = worldState.nationManager.getNationState(nationId);
        if (nationState == null)
            return false;

        for (Long entityId : sector.entityIds) {
            Entity entity = worldState.entitiesById.get(entityId);
            if (entity != null && entity.ownerNationId != null) {
                // 检查是否为同盟国
                if (nationState.isAlliedWith(entity.ownerNationId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 计算两点之间的距离（GU）。
     *
     * @param a 点A
     * @param b 点B
     * @return 距离
     */
    private double calculateDistance(Vec2d a, Vec2d b) {
        if (a == null || b == null) {
            return Double.MAX_VALUE;
        }
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 将 SectorCoord 转换为字符串键。
     *
     * @param coord 星区坐标
     * @return 字符串键（格式：q:{q},r:{r}）
     */
    private String coordToKey(SectorCoord coord) {
        return "q:" + coord.q() + ",r:" + coord.r();
    }

    /**
     * 从字符串键解析回 SectorCoord。
     *
     * @param key 字符串键
     * @return SectorCoord 对象，解析失败返回 null
     */
    private SectorCoord keyToCoord(String key) {
        try {
            String[] parts = key.split(",");
            int q = Integer.parseInt(parts[0].split(":")[1]);
            int r = Integer.parseInt(parts[1].split(":")[1]);
            return new SectorCoord(q, r);
        } catch (Exception e) {
            return null;
        }
    }
}
