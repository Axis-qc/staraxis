package staraxis.game.player;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * PlayerManager（玩家管理器）
 *
 * 集中管理所有玩家的运行时状态。
 * 与 NationManager 平行，共同构成所有权管理层。
 *
 * 核心职责：
 * 1. 维护 playerId 到 PlayerState 的映射
 * 2. 提供玩家状态的注册、查询、遍历
 * 3. 管理玩家与国家的关联关系
 * 4. 所有权变更由 AssetManager 统一处理，本类不处理资产
 */
public class PlayerManager {

    /** playerId -> PlayerState 映射。 */
    private final Map<String, PlayerState> playerStates = new HashMap<>();

    /**
     * 注册玩家（创建运行时状态）。
     *
     * @param playerId 玩家唯一标识
     * @return 新创建的 PlayerState 实例
     * @throws IllegalArgumentException 如果玩家已存在
     */
    public PlayerState registerPlayer(String playerId) {
        if (playerStates.containsKey(playerId)) {
            throw new IllegalArgumentException("玩家已存在: " + playerId);
        }
        PlayerState state = new PlayerState(playerId);
        playerStates.put(playerId, state);
        return state;
    }

    /**
     * 获取指定玩家的运行时状态。
     *
     * @param playerId 玩家唯一标识
     * @return PlayerState 实例，如果不存在则返回 null
     */
    public PlayerState getPlayerState(String playerId) {
        return playerStates.get(playerId);
    }

    /**
     * 检查玩家是否存在。
     *
     * @param playerId 玩家唯一标识
     * @return 如果存在则返回 true
     */
    public boolean hasPlayer(String playerId) {
        return playerStates.containsKey(playerId);
    }

    /**
     * 获取所有玩家ID。
     *
     * @return 玩家ID集合
     */
    public Set<String> getAllPlayerIds() {
        return new HashSet<>(playerStates.keySet());
    }

    /**
     * 获取所有玩家运行时状态。
     *
     * @return PlayerState 集合
     */
    public Collection<PlayerState> getAllPlayerStates() {
        return new HashSet<>(playerStates.values());
    }

    /**
     * 设置玩家所属国家。
     *
     * @param playerId 玩家唯一标识
     * @param nationId 国家ID（为 null 表示解除关联）
     */
    public void setPlayerNation(String playerId, String nationId) {
        PlayerState state = playerStates.get(playerId);
        if (state == null) {
            throw new IllegalArgumentException("玩家不存在: " + playerId);
        }
        state.nationId = nationId;
    }

    /**
     * 获取玩家所属国家ID。
     *
     * @param playerId 玩家唯一标识
     * @return 国家ID，无关联时返回 null
     */
    public String getPlayerNation(String playerId) {
        PlayerState state = playerStates.get(playerId);
        return state != null ? state.nationId : null;
    }

    /**
     * 解除玩家与国家关联（保留个人资产）。
     *
     * @param playerId 玩家唯一标识
     */
    public void removePlayerFromNation(String playerId) {
        PlayerState state = playerStates.get(playerId);
        if (state != null) {
            state.nationId = null;
        }
    }

    /**
     * 获取玩家数量。
     *
     * @return 玩家总数
     */
    public int getPlayerCount() {
        return playerStates.size();
    }

    /**
     * 清除所有玩家状态（用于重新开始或测试）。
     */
    public void clear() {
        playerStates.clear();
    }
}
