package staraxis.game.world;

/**
 * WorldGenConfig
 *
 * 纯数据配置：由外部（webnet）构造并传入 game。
 */
public class WorldGenConfig {

    /**
     * worldSeed：世界生成种子（允许为空，表示由外部决定默认策略）。
     */
    public String worldSeed;

    /**
     * worldRadius：六边形星区半径（以中心 (0,0) 为起点的环数）。
     */
    public int worldRadius;

    /**
     * galaxyShape：星系形状（数据驱动占位字段）。
     */
    public String galaxyShape;

    /**
     * playerNationDef：玩家选择/自定义的国家定义。
     */
    public staraxis.game.nation.NationDef playerNationDef;

    public WorldGenConfig() {
    }
}
