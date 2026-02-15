package staraxis.game.world;

/**
 * WorldType
 *
 * 世界类型：用于区分单人/多人/服务器世界在规则与权限上的差异喵。
 */
public enum WorldType {

    /**
     * 单人世界：允许玩家设置时间推进速度喵。
     */
    SINGLE_PLAYER,

    /**
     * 多人世界：允许玩家设置时间推进速度（由房主/房间规则决定，模拟层仍需校验）喵。
     */
    MULTI_PLAYER,

    /**
     * 服务器世界：禁止客户端设置时间推进速度，完全由服务器策略决定喵。
     */
    SERVER
}
