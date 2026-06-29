package staraxis.game.world;

/**
 * WorldGenConfig
 *
 * 纯数据配置：由外部（webnet）构造并传入 game。
 */
public class WorldGenConfig {

    public String worldSeed;

    /** 恒星系数量（500~10000），决定生成多少个恒星系喵。 */
    public int systemCount = 500;

    public WorldType worldType = WorldType.SINGLE_PLAYER;

    public staraxis.game.nation.NationDef playerNationDef;

    public WorldGenConfig() {
    }
}
