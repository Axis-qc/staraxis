package staraxis.game.nation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * NationManager（国家管理器）
 *
 * 作用：集中管理所有国家的运行时状态，提供玩家-国家映射、国家查询、关系维护等核心服务。
 *
 * 核心职责：
 * 1. 维护国家ID到NationState的映射
 * 2. 管理玩家ID到国家ID的映射（一个玩家只能属于一个国家）
 * 3. 提供国家间的外交关系查询与更新
 * 4. 支持可见性计算所需的国家状态查询
 * 5. 确保国家状态变更的权威性和一致性
 *
 * 注意事项：
 * - 本类是权威模拟层的一部分，所有状态变更必须在 game 模块内进行
 * - 国家管理器在 WorldState 中实例化，随世界状态一起持久化
 * - 外交关系是单向的，但通常需要双向设置以保持一致性
 */
public class NationManager {

    /** 国家ID到NationState的映射。 */
    private final Map<String, NationState> nationStates = new HashMap<>();

    /** 玩家ID（playerId）到国家ID（nationId）的映射（一个玩家只能属于一个国家）。 */
    private final Map<String, String> playerToNation = new HashMap<>();

    /**
     * 注册新国家（从 NationDef 创建运行时状态）。
     *
     * @param nationId 国家唯一标识
     * @return 新创建的 NationState 实例
     */
    public NationState registerNation(String nationId) {
        if (nationStates.containsKey(nationId)) {
            throw new IllegalArgumentException("国家已存在: " + nationId);
        }
        NationState state = new NationState(nationId);
        nationStates.put(nationId, state);
        return state;
    }

    /**
     * 获取指定国家的运行时状态。
     *
     * @param nationId 国家唯一标识
     * @return NationState 实例，如果不存在则返回 null
     */
    public NationState getNationState(String nationId) {
        return nationStates.get(nationId);
    }

    /**
     * 获取所有国家ID。
     *
     * @return 国家ID集合
     */
    public Set<String> getAllNationIds() {
        return new HashSet<>(nationStates.keySet());
    }

    /**
     * 获取所有国家运行时状态。
     *
     * @return NationState 集合
     */
    public Set<NationState> getAllNationStates() {
        return new HashSet<>(nationStates.values());
    }

    /**
     * 检查国家是否存在。
     *
     * @param nationId 国家唯一标识
     * @return 如果存在则返回 true
     */
    public boolean hasNation(String nationId) {
        return nationStates.containsKey(nationId);
    }

    /**
     * 将玩家关联到国家。
     *
     * @param playerId 玩家ID
     * @param nationId 国家唯一标识
     */
    public void assignPlayerToNation(String playerId, String nationId) {
        if (!nationStates.containsKey(nationId)) {
            throw new IllegalArgumentException("国家不存在: " + nationId);
        }
        // 如果玩家已有归属，先移除旧关联
        String oldNationId = playerToNation.get(playerId);
        if (oldNationId != null) {
            NationState oldState = nationStates.get(oldNationId);
            if (oldState != null) {
                oldState.removePlayer(playerId);
            }
        }
        // 设置新关联
        playerToNation.put(playerId, nationId);
        NationState state = nationStates.get(nationId);
        state.addPlayer(playerId);
    }

    /**
     * 获取玩家所属的国家ID。
     *
     * @param playerId 玩家ID
     * @return 国家ID，如果玩家无归属则返回 null
     */
    public String getNationIdByPlayer(String playerId) {
        return playerToNation.get(playerId);
    }

    /**
     * 获取玩家所属的国家运行时状态。
     *
     * @param playerId 玩家ID
     * @return NationState 实例，如果玩家无归属则返回 null
     */
    public NationState getNationStateByPlayer(String playerId) {
        String nationId = playerToNation.get(playerId);
        return nationId != null ? nationStates.get(nationId) : null;
    }

    /**
     * 移除玩家的国家归属。
     *
     * @param playerId 玩家ID
     */
    public void removePlayerFromNation(String playerId) {
        String nationId = playerToNation.remove(playerId);
        if (nationId != null) {
            NationState state = nationStates.get(nationId);
            if (state != null) {
                state.removePlayer(playerId);
            }
        }
    }

    /**
     * 设置两国间的外交关系（双向设置以保持一致性）。
     *
     * @param nationIdA    国家A ID
     * @param nationIdB    国家B ID
     * @param relationType 关系类型（如 "ALLIANCE", "PEACE", "WAR", "NEUTRAL"）
     */
    public void setDiplomaticRelation(String nationIdA, String nationIdB, String relationType) {
        NationState stateA = nationStates.get(nationIdA);
        NationState stateB = nationStates.get(nationIdB);
        if (stateA == null || stateB == null) {
            throw new IllegalArgumentException("国家不存在: " + (stateA == null ? nationIdA : nationIdB));
        }
        stateA.setDiplomaticRelation(nationIdB, relationType);
        stateB.setDiplomaticRelation(nationIdA, relationType);
    }

    /**
     * 获取两国间的外交关系。
     *
     * @param nationIdA 国家A ID
     * @param nationIdB 国家B ID
     * @return 关系类型（从国家A的视角）
     */
    public String getDiplomaticRelation(String nationIdA, String nationIdB) {
        NationState stateA = nationStates.get(nationIdA);
        if (stateA == null) {
            throw new IllegalArgumentException("国家不存在: " + nationIdA);
        }
        return stateA.getDiplomaticRelation(nationIdB);
    }

    /**
     * 检查两国是否处于战争状态。
     *
     * @param nationIdA 国家A ID
     * @param nationIdB 国家B ID
     * @return 如果处于战争状态则返回 true
     */
    public boolean areNationsAtWar(String nationIdA, String nationIdB) {
        NationState stateA = nationStates.get(nationIdA);
        if (stateA == null)
            return false;
        return stateA.isAtWarWith(nationIdB);
    }

    /**
     * 检查两国是否处于同盟状态。
     *
     * @param nationIdA 国家A ID
     * @param nationIdB 国家B ID
     * @return 如果处于同盟状态则返回 true
     */
    public boolean areNationsAllied(String nationIdA, String nationIdB) {
        NationState stateA = nationStates.get(nationIdA);
        if (stateA == null)
            return false;
        return stateA.isAlliedWith(nationIdB);
    }

    /**
     * 根据国家ID获取所有敌对国家的ID。
     *
     * @param nationId 国家ID
     * @return 敌对国家的ID集合
     */
    public Set<String> getEnemyNationIds(String nationId) {
        Set<String> enemies = new HashSet<>();
        NationState state = nationStates.get(nationId);
        if (state == null)
            return enemies;
        for (Map.Entry<String, String> entry : state.diplomaticRelations.entrySet()) {
            if ("WAR".equals(entry.getValue())) {
                enemies.add(entry.getKey());
            }
        }
        return enemies;
    }

    /**
     * 根据国家ID获取所有同盟国家的ID。
     *
     * @param nationId 国家ID
     * @return 同盟国家的ID集合
     */
    public Set<String> getAlliedNationIds(String nationId) {
        Set<String> allies = new HashSet<>();
        NationState state = nationStates.get(nationId);
        if (state == null)
            return allies;
        for (Map.Entry<String, String> entry : state.diplomaticRelations.entrySet()) {
            if ("ALLIANCE".equals(entry.getValue())) {
                allies.add(entry.getKey());
            }
        }
        return allies;
    }

    /**
     * 清除所有国家状态（用于重新开始或测试）。
     */
    public void clear() {
        nationStates.clear();
        playerToNation.clear();
    }

    /**
     * 获取国家数量。
     *
     * @return 国家总数
     */
    public int getNationCount() {
        return nationStates.size();
    }

    /**
     * 获取玩家数量。
     *
     * @return 已分配国家的玩家总数
     */
    public int getPlayerCount() {
        return playerToNation.size();
    }
}