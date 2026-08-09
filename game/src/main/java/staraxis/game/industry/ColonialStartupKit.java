package staraxis.game.industry;

/**
 * ColonialStartupKit（殖民地初始套件）
 *
 * 殖民成功后为殖民地初始化工矿业所需的起始资源常量（G2 第一阶段）。
 *
 * 能源来源策略说明（重要）：
 * - 水电解配方需要消耗能源（energyCost），但不能让配方凭空生成能源。
 * - 第一阶段由"殖民初始能源库存"提供闭环能源：殖民成功时向行星本地库存
 *   预存固定量 ENERGY（本类 INITIAL_ENERGY），电解槽每日从同一库存消耗。
 * - 这是 G2 第一阶段临时资源：正式发电设施/能源系统（发电站、反应堆等）
 *   落地前，初始能源库存作为唯一能源来源；该临时策略后续应被移除。
 */
public final class ColonialStartupKit {

    private ColonialStartupKit() {
    }

    /** 殖民初始能源库存（单位）。G2 第一阶段临时资源：正式发电系统落地前提供电解能源。 */
    public static final double INITIAL_ENERGY = 100.0;

    /** 水采集设施每日固定产出（单位/日）。 */
    public static final double WATER_EXTRACTION_PER_DAY = 20.0;
}
