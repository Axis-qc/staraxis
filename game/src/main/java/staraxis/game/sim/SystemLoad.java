package staraxis.game.sim;

/**
 * SystemLoad（恒星系负载追踪）。
 *
 * 记录每个恒星系每 tick 的计算负载（实体数），
 * 用于 LPT 分配算法的输入和动态重平衡决策。
 */
public class SystemLoad {

    /** 恒星系 ID。 */
    public final long systemId;

    /** 当前星系实体总数（包括静态天体 + 动态实体）。 */
    public int entityCount;

    /** 动态实体数（舰船等活跃实体），变动时触发重平衡检测。 */
    public int dynamicCount;

    /** 最后活跃 tick，用于判断冷/热。 */
    public long lastActiveTick;

    public SystemLoad(long systemId, int entityCount) {
        this.systemId = systemId;
        this.entityCount = entityCount;
        this.dynamicCount = 0;
        this.lastActiveTick = 0;
    }

    /** 是否为热星系（entityCount > HOT_THRESHOLD）。 */
    public boolean isHot(int hotThreshold) {
        return entityCount > hotThreshold;
    }

    /**
     * 获取有效权重。
     * 冷星系按固定小权重计入，避免冷星系过多时一个线程负载畸高。
     */
    public int effectiveWeight() {
        if (entityCount < staraxis.game.sim.LptAssigner.HOT_THRESHOLD) {
            return staraxis.game.sim.LptAssigner.COLD_SYSTEM_WEIGHT;
        }
        return entityCount;
    }

    /** 复制一份快照（供分配算法使用，不引用原始数据）。 */
    public SystemLoad copy() {
        SystemLoad c = new SystemLoad(systemId, entityCount);
        c.dynamicCount = dynamicCount;
        c.lastActiveTick = lastActiveTick;
        return c;
    }
}
