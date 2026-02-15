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
 * 作用：根据玩家的出生策略（SpawnStrategy）为国家选择初始星系与首都星球，
 * 并通过 NationAssetManager 完成实体所有权与国家运行状态字段的初始化。
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
        if (nationId == null) {
            return;
        }

        AstroData astro = worldState.astro;
        if (astro == null) {
            return;
        }

        // 1. 收集所有拥有至少一颗行星的星系
        List<StarSystem> candidateSystems = new ArrayList<>();
        for (StarSystem system : astro.getSystemsView()) {
            if (system == null || system.planets == null || system.planets.isEmpty()) {
                continue;
            }
            candidateSystems.add(system);
        }

        if (candidateSystems.isEmpty()) {
            return;
        }

        // 2. 使用基于世界种子的确定性随机源
        long worldSeedHash = 0L;
        if (worldState.astro != null && !worldState.astro.getSystemsView().isEmpty()) {
            // 这里暂时使用第一个系统ID作为简单的种子混合来源，后续可与 SimulationTime 或专用种子结合。
            worldSeedHash = candidateSystems.get(0).systemId;
        }
        Random random = new Random(worldSeedHash);

        // 3. 随机选择一个星系
        StarSystem chosenSystem = candidateSystems.get(random.nextInt(candidateSystems.size()));
        if (chosenSystem.planets == null || chosenSystem.planets.isEmpty()) {
            return;
        }

        // 4. 随机选择该星系中的一颗行星作为首都行星
        PlanetBody chosenPlanet = chosenSystem.planets.get(random.nextInt(chosenSystem.planets.size()));

        // 5. 通过资产管理器分配行星给国家
        worldState.nationAssetManager.assignEntityToNation(chosenPlanet.entityId, nationId);

        // 6. 更新国家运行时状态中的出生星系与首都行星字段
        NationState nationState = worldState.nationManager.getNationState(nationId);
        if (nationState != null) {
            nationState.spawnSystemEntityId = chosenSystem.systemId;
            nationState.capitalPlanetEntityId = chosenPlanet.entityId;
        }
    }
}
