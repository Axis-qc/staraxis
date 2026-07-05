package staraxis.game.nation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import staraxis.game.astro.AstroData;
import staraxis.game.astro.PlanetBody;
import staraxis.game.astro.StarSystem;
import staraxis.game.state.WorldState;

/**
 * NationSpawnService（国家出生点服务）
 *
 * 作用：根据玩家的出生策略（SpawnStrategy）为国家选择初始星系与首都星球。
 *
 * 注意：当前已禁用，等 AssetManager 统一处理所有权归属喵。
 */
public class NationSpawnService {

    /** 权威世界状态引用。 */
    private final WorldState worldState;

    /**
     * 构造函数。
     *
     * @param worldState 世界权威状态（WorldState）
     */
    public NationSpawnService(WorldState worldState) {
        this.worldState = worldState;
    }

    /**
     * 为指定国家随机选择一个星系及其下的一颗行星作为初始出生点。
     *
     * 说明：
     * - 从 AstroData 中筛选出“至少包含一颗行星”的星系列表。
     * - 使用确定性的随机源（基于世界种子）选择一个星系与一颗行星。
     * - 通过 NationAssetManager 将该行星分配给目标国家，并更新 NationState.spawnSystemEntityId 与
     * capitalPlanetEntityId。
     *
     * @param nationId 目标国家ID
     */
    public void assignRandomHomeSystemAndCapital(String nationId) {
        // TODO AssetManager 统一处理：当前禁用开局归属分配，待后续流程设计时重新实现喵
        staraxis.game.log.GameLog.log("NationSpawnService.assignRandomHomeSystemAndCapital disabled: pending AssetManager 喵");
    }
}
