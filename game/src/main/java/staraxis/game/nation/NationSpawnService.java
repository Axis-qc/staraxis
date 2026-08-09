package staraxis.game.nation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import staraxis.game.astro.PlanetBody;
import staraxis.game.astro.StarSystem;
import staraxis.game.ship.ShipSpawnService;
import staraxis.game.space.SpacePosition;
import staraxis.game.state.WorldState;

/**
 * NationSpawnService（国家出生点服务）喵。
 *
 * 作用：在玩家选定母星系后，执行国家注册、星系归属分配、初始舰队生成喵。
 *
 * 世界观：玩家扮演从系外而来的探索者，舰队通过虫洞在目标星系最远行星轨道外侧出现喵。
 */
public class NationSpawnService {

    // ── 初始舰队配置 ──
    /** 探索船蓝图ID。 */
    private static final String DESIGN_EXPLORER = "default_explorer_ship";
    /** 殖民船蓝图ID（与 ShipDesign.DESIGN_ID_COLONY 保持单一口径）喵。 */
    private static final String DESIGN_COLONY = staraxis.game.ship.ShipDesign.DESIGN_ID_COLONY;
    /** 护卫舰蓝图ID。 */
    private static final String DESIGN_FRIGATE = "default_frigate";

    /** 舰队刷出位置偏移系数：最远轨道距离乘以该系数作为实际刷出距离（轨道外 20%）。 */
    private static final double SPAWN_DIST_FACTOR = 1.2;
    /** 无行星时的兜底刷出距离系数：取重力井半径的 50%。 */
    private static final double FALLBACK_SPAWN_DIST_FACTOR = 0.5;
    /** 舰船间分散偏移（GU），避免 4 艘舰船堆叠在同一位置。 */
    private static final double SHIP_SPREAD_GU = 80.0;

    /** 权威世界状态引用。 */
    private final WorldState worldState;

    /**
     * 出生结果记录。
     *
     * @param shipIds        生成的舰船实体ID列表
     * @param fleetCenterPos 舰队质心世界坐标（client 用于镜头定位和虫洞效果）
     */
    public record SpawnResult(List<Long> shipIds, SpacePosition fleetCenterPos) {
    }

    /**
     * 构造函数。
     *
     * @param worldState 世界权威状态（WorldState）
     */
    public NationSpawnService(WorldState worldState) {
        this.worldState = worldState;
    }

    /**
     * 在玩家选定的星系设置国家开局喵。
     *
     * 执行流程：
     * 1. 注册国家（NationManager.registerNation）
     * 2. 设置星系归属（StarSystem.assignOwnership）
     * 3. 计算舰队刷出位置：最远行星轨道距离 x 1.2，随机角度
     * 4. 生成 4 艘初始舰船（1 探索 + 1 殖民 + 2 护卫），在刷出位置分散排列
     * 5. 通过 AssetManager 分配舰船国家归属
     *
     * @param nationId 目标国家ID
     * @param system   目标恒星系
     * @return SpawnResult 包含舰船ID列表和舰队质心坐标
     */
    public SpawnResult setupPlayerNationAt(String nationId, StarSystem system) {
        if (nationId == null || nationId.isBlank()) {
            throw new IllegalArgumentException("nationId_required");
        }
        if (system == null) {
            throw new IllegalArgumentException("system_required");
        }

        // 1. 注册国家喵
        if (!worldState.nationManager.hasNation(nationId)) {
            worldState.nationManager.registerNation(nationId);
        }

        // 2. 设置星系归属喵
        system.assignOwnership(nationId);

        // 3. 计算舰队刷出位置喵
        SpacePosition fleetCenter = calculateSpawnPosition(system);

        // 4. 生成初始舰队（4 艘，在 fleetCenter 周围分散）喵
        List<Long> shipIds = spawnInitialFleet(nationId, system.systemId, fleetCenter);

        // 5. 分配舰船国家归属喵
        for (long shipId : shipIds) {
            worldState.assetManager.assignToNation(shipId, nationId);
        }

        return new SpawnResult(shipIds, fleetCenter);
    }

    /**
     * 计算舰队刷出位置：最远行星轨道外侧 20%，随机角度喵。
     *
     * 若无行星则 fallback 到重力井半径的 50%喵。
     *
     * @param system 目标恒星系
     * @return 舰队世界坐标
     */
    private SpacePosition calculateSpawnPosition(StarSystem system) {
        double maxOrbit = 0.0;
        if (system.planets != null) {
            for (PlanetBody planet : system.planets) {
                if (planet != null && planet.semiMajorAxisGU > maxOrbit) {
                    maxOrbit = planet.semiMajorAxisGU;
                }
            }
        }

        double spawnDist;
        if (maxOrbit > 0) {
            spawnDist = maxOrbit * SPAWN_DIST_FACTOR;
        } else {
            spawnDist = system.gravityWellRadiusGU * FALLBACK_SPAWN_DIST_FACTOR;
        }

        // 随机角度（弧度），使舰队不总是刷在同一个方位喵
        double randomAngle = Math.random() * 2.0 * Math.PI;

        double sx = system.galaxyPos.x() + spawnDist * Math.cos(randomAngle);
        double sy = system.galaxyPos.y(); // 星系盘面 Y 不变
        double sz = system.galaxyPos.z() + spawnDist * Math.sin(randomAngle);

        return new SpacePosition(sx, sy, sz);
    }

    /**
     * 在指定位置生成 4 艘初始舰船喵。
     *
     * 舰船配置：
     * - 1 艘探索船（EXPLORER）
     * - 1 艘殖民船（COLONY）
     * - 2 艘护卫舰（FRIGATE）
     *
     * 每艘舰船在 fleetCenter 周围分散排列（x 字形散开），避免堆叠喵。
     *
     * @param nationId    所属国家ID
     * @param systemId    所属星系ID
     * @param fleetCenter 舰队质心世界坐标
     * @return 生成的舰船实体ID列表
     */
    private List<Long> spawnInitialFleet(String nationId, long systemId, SpacePosition fleetCenter) {
        List<Long> ids = new ArrayList<>();

        // 4 艘舰船沿 x 字形散开（NE, NW, SE, SW），各偏移 SHIP_SPREAD_GU 喵
        double[][] offsets = {
                { SHIP_SPREAD_GU, 0, SHIP_SPREAD_GU }, // NE
                { -SHIP_SPREAD_GU, 0, SHIP_SPREAD_GU }, // NW
                { SHIP_SPREAD_GU, 0, -SHIP_SPREAD_GU }, // SE
                { -SHIP_SPREAD_GU, 0, -SHIP_SPREAD_GU }, // SW
        };

        String[] designs = { DESIGN_EXPLORER, DESIGN_COLONY, DESIGN_FRIGATE, DESIGN_FRIGATE };
        String[][] flagSets = {
                { "INITIAL_FLEET", "EXPLORER" },
                { "INITIAL_FLEET", staraxis.game.ship.ShipDesign.FLAG_COLONY },
                { "INITIAL_FLEET", "FRIGATE" },
                { "INITIAL_FLEET", "FRIGATE" },
        };

        for (int i = 0; i < 4; i++) {
            SpacePosition pos = new SpacePosition(
                    fleetCenter.x() + offsets[i][0],
                    fleetCenter.y() + offsets[i][1],
                    fleetCenter.z() + offsets[i][2]);

            Set<String> flags = new LinkedHashSet<>();
            for (String f : flagSets[i]) {
                flags.add(f);
            }

            long shipId = ShipSpawnService.spawnShipAtPosition(
                    worldState, nationId, pos, systemId, flags, designs[i]);

            if (shipId > 0) {
                ids.add(shipId);
            }
        }

        return ids;
    }

    /**
     * @deprecated 已被 setupPlayerNationAt 替代，保留仅为兼容旧引用喵。
     */
    @Deprecated
    public void assignRandomHomeSystemAndCapital(String nationId) {
        // 已废弃：开局流程改为玩家自选母星系，此方法不再使用喵
    }
}
