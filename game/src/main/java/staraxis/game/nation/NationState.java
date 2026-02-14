package staraxis.game.nation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * NationState（国家运行时状态）
 *
 * 作用：管理国家的权威运行时状态，包括玩家归属、外交关系、可见性状态等。
 *
 * 核心职责：
 * 1. 维护国家ID与玩家ID的映射关系
 * 2. 管理国家间的外交关系
 * 3. 跟踪国家的探索与可见性状态
 * 4. 提供国家运行时数据的查询接口
 *
 * 注意事项：
 * - 本类是权威模拟层的一部分，所有状态变更必须在 game 模块内进行
 * - 国家状态的变化会影响可见性计算和游戏规则
 * - 外交关系状态影响可见性、贸易、战争等游戏机制
 */
public class NationState {

    /** 国家唯一标识（与 NationDef.id 一致）。 */
    public final String nationId;

    /** 关联的玩家ID列表（一个国可以有多位玩家）。 */
    public final Set<String> playerIds = new HashSet<>();

    /** 外交关系映射：目标国家ID -> 关系类型（如：ALLIANCE, PEACE, WAR, NEUTRAL）。 */
    public final Map<String, String> diplomaticRelations = new HashMap<>();

    /** 已探索的星区坐标集合（记忆层）。 */
    public final Set<String> exploredSectorCoords = new HashSet<>();

    /** 当前可见的星区坐标集合（实时层）。 */
    public final Set<String> visibleSectorCoords = new HashSet<>();

    /** 传感器范围（基础+升级，单位：GU）。 */
    public double sensorRangeGU = 100.0;

    /** 国家颜色（用于地图渲染，格式：0xRRGGBB）。 */
    public int colorRgb = 0xFFFFFF;

    /** 国家旗帜标识（资源路径）。 */
    public String flagAssetPath;

    /** 国家是否处于活跃状态（true=正常，false=灭亡/休眠）。 */
    public boolean active = true;

    /** 国家名称（从 NationDef 同步的纯文本）。 */
    public String name;

    /** 政体引用 ID（从 NationDef 同步）。 */
    public String governmentId;

    /** 出生星系实体 ID（权威绑定结果）。 */
    public long spawnSystemEntityId;

    /**
     * 构造函数：创建指定ID的国家运行时状态。
     *
     * @param nationId 国家唯一标识
     */
    public NationState(String nationId) {
        this.nationId = nationId;
    }

    /**
     * 添加玩家归属关联。
     *
     * @param playerId 玩家ID
     */
    public void addPlayer(String playerId) {
        playerIds.add(playerId);
    }

    /**
     * 移除玩家归属关联。
     *
     * @param playerId 玩家ID
     */
    public void removePlayer(String playerId) {
        playerIds.remove(playerId);
    }

    /**
     * 检查是否有指定玩家归属。
     *
     * @param playerId 玩家ID
     * @return 如果玩家属于该国则返回 true
     */
    public boolean hasPlayer(String playerId) {
        return playerIds.contains(playerId);
    }

    /**
     * 设置外交关系。
     *
     * @param targetNationId 目标国家ID
     * @param relationType   关系类型（如 "ALLIANCE", "PEACE", "WAR", "NEUTRAL"）
     */
    public void setDiplomaticRelation(String targetNationId, String relationType) {
        diplomaticRelations.put(targetNationId, relationType);
    }

    /**
     * 获取与目标国家的外交关系。
     *
     * @param targetNationId 目标国家ID
     * @return 关系类型，如果未定义则返回 "NEUTRAL"
     */
    public String getDiplomaticRelation(String targetNationId) {
        return diplomaticRelations.getOrDefault(targetNationId, "NEUTRAL");
    }

    /**
     * 检查是否与目标国家处于战争状态。
     *
     * @param targetNationId 目标国家ID
     * @return 如果关系为 "WAR" 则返回 true
     */
    public boolean isAtWarWith(String targetNationId) {
        return "WAR".equals(getDiplomaticRelation(targetNationId));
    }

    /**
     * 检查是否与目标国家处于同盟状态。
     *
     * @param targetNationId 目标国家ID
     * @return 如果关系为 "ALLIANCE" 则返回 true
     */
    public boolean isAlliedWith(String targetNationId) {
        return "ALLIANCE".equals(getDiplomaticRelation(targetNationId));
    }

    /**
     * 添加探索星区。
     *
     * @param sectorCoord 星区坐标（字符串表示，如 "0,0" 或 "q:0,r:0"）
     */
    public void addExploredSector(String sectorCoord) {
        exploredSectorCoords.add(sectorCoord);
    }

    /**
     * 添加可见星区。
     *
     * @param sectorCoord 星区坐标
     */
    public void addVisibleSector(String sectorCoord) {
        visibleSectorCoords.add(sectorCoord);
    }

    /**
     * 移除可见星区。
     *
     * @param sectorCoord 星区坐标
     */
    public void removeVisibleSector(String sectorCoord) {
        visibleSectorCoords.remove(sectorCoord);
    }

    /**
     * 检查星区是否已探索（记忆层）。
     *
     * @param sectorCoord 星区坐标
     * @return 如果已探索则返回 true
     */
    public boolean isSectorExplored(String sectorCoord) {
        return exploredSectorCoords.contains(sectorCoord);
    }

    /**
     * 检查星区是否当前可见（实时层）。
     *
     * @param sectorCoord 星区坐标
     * @return 如果当前可见则返回 true
     */
    public boolean isSectorVisible(String sectorCoord) {
        return visibleSectorCoords.contains(sectorCoord);
    }

    /**
     * 获取传感器范围（GU）。
     *
     * @return 传感器范围
     */
    public double getSensorRangeGU() {
        return sensorRangeGU;
    }

    /**
     * 设置传感器范围（GU）。
     *
     * @param sensorRangeGU 传感器范围
     */
    public void setSensorRangeGU(double sensorRangeGU) {
        this.sensorRangeGU = sensorRangeGU;
    }

    /**
     * 激活/停用国家。
     *
     * @param active true=激活，false=停用
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * 检查国家是否活跃。
     *
     * @return 如果活跃则返回 true
     */
    public boolean isActive() {
        return active;
    }
}