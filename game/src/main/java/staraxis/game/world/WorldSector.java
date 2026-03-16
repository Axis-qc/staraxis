package staraxis.game.world;

import staraxis.game.world.hex.SectorCoord;

/**
 * WorldSector（世界星区）喵。
 *
 * 作用：
 * - 存储星区的基础地理信息（坐标、中心点）。
 * - 维护星区内的实体列表。
 * - 缓存各国在该星区的探测等级，支持快速可见性判断喵。
 */
public class WorldSector {

    public final SectorCoord coord;

    /**
     * 星区中心点（权威世界坐标，GU，2D）。
     */
    public final Vec2d centerWorldGU;

    /**
     * 该星区内持有的实体 ID 列表。
     */
    public final java.util.List<Long> entityIds = new java.util.ArrayList<>();

    /**
     * 预留：该星区归属/势力占位（可为空）。
     */
    public String ownerNationId;

    /**
     * 各国在该星区的探测等级缓存（nationId -> detectorLevel）喵。
     *
     * 说明：
     * - 等级范围：-1（无探测）到 10（最高探测）喵。
     * - 由 IntelSystem 在 markDirty 触发重建时更新喵。
     * - Webnet 快照生成时直接读取，避免重复计算喵。
     */
    public final java.util.concurrent.ConcurrentHashMap<String, Integer> nationDetectorLevels = new java.util.concurrent.ConcurrentHashMap<>();

    public WorldSector(SectorCoord coord, Vec2d centerWorldGU) {
        this.coord = coord;
        this.centerWorldGU = centerWorldGU;
    }

    /**
     * 获取指定国家在该星区的探测等级喵。
     *
     * @param nationId 国家ID
     * @return 探测等级（-1 表示无探测，0-10 表示有效等级）
     */
    public int getDetectorLevel(String nationId) {
        if (nationId == null || nationId.isBlank()) {
            return -1;
        }
        return nationDetectorLevels.getOrDefault(nationId, -1);
    }

    /**
     * 设置指定国家在该星区的探测等级喵。
     *
     * @param nationId 国家ID
     * @param level    探测等级（-1 到 10）
     */
    public void setDetectorLevel(String nationId, int level) {
        if (nationId == null || nationId.isBlank()) {
            return;
        }
        if (level < 0) {
            nationDetectorLevels.remove(nationId);
        } else {
            nationDetectorLevels.put(nationId, Math.min(10, level));
        }
    }
}
