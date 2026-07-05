package staraxis.game.ship;

import staraxis.game.astro.StarSystem;
import staraxis.game.entity.EntityType;
import staraxis.game.space.SpacePosition;
import staraxis.game.state.WorldState;

/**
 * ShipSpawnService（舰船生成服务）喵。
 *
 * 作用：
 * - 在 game 模块集中管理舰船生成权威逻辑，确保所有状态修改在模拟层执行喵。
 * - 提供初始舰船生成、舰船装配、舰船销毁等核心服务喵。
 *
 * 规则：
 * - 所有方法必须接收 WorldState 作为第一个参数，确保在权威上下文中执行喵。
 * - 舰船属性优先从配置加载，硬编码仅为兜底喵。
 * - 实体 ID 必须通过 WorldState.generateEntityId() 生成，确保全局唯一性喵。
 */
public final class ShipSpawnService {

    private ShipSpawnService() {
    }

    /**
     * 为指定国家在出生星系生成初始殖民舰喵。
     *
     * 策略：
     * - 使用星系中心坐标（StarSystem.centerWorldGU）并偏移 500 GU 避免与星体重叠喵。
     * - 舰船标记为 INITIAL_SPAWN_SHIP 供前端特殊处理喵。
     * - 硬编码初始属性：耐久 1.0、能源 100、燃料 100喵。
     * - 生成后自动注册到 WorldState 并分配国家归属喵。
     *
     * @param worldState 权威世界状态（必须非空）
     * @param nationId   所属国家ID（必须非空）
     * @param spawnSystem 出生星系（必须非空）
     * @return 生成的舰船实体ID，失败时返回 -1
     */
    public static long spawnInitialShip(WorldState worldState, String nationId, StarSystem spawnSystem) {
        if (worldState == null) {
            throw new IllegalArgumentException("worldState_required");
        }
        if (nationId == null || nationId.isBlank()) {
            throw new IllegalArgumentException("nationId_required");
        }
        if (spawnSystem == null) {
            throw new IllegalArgumentException("spawnSystem_required");
        }

        // 1. 生成实体ID（全局唯一）喵
        long entityId = worldState.generateEntityId();
        if (entityId <= 0) {
            return -1;
        }

        // 2. 计算出生位置：星系中心偏移 500 GU（避免与星体重叠）喵
        SpacePosition systemCenter = spawnSystem.galaxyPos;
        if (systemCenter == null) {
            return -1;
        }

        // 固定偏移向量（500 GU 在 X 轴正方向）喵，映射到 3D XZ 平面
        SpacePosition shipPos = new SpacePosition(systemCenter.x() + 500.0, systemCenter.y(), systemCenter.z());

        // 3. 创建舰船实体喵
        ShipBody ship = new ShipBody();
        ship.entityId = entityId;
        ship.entityType = EntityType.SHIP;
        ship.designId = null; // 暂不依赖设计文件喵
        ship.posWorldGU = shipPos;
        ship.velWorldGU = SpacePosition.ORIGIN;
        ship.systemId = spawnSystem.systemId; // 所属星系ID喵

        // 硬编码初始属性喵
        ship.hpHull = 1.0;      // 满耐久喵
        ship.power = 100.0;     // 初始能源喵
        ship.fuelMass = 100.0;  // 初始燃料喵
        ship.customFlags.add("INITIAL_SPAWN_SHIP"); // 标记为初始出生舰船喵

        // 初始化舰船性能数据（从 ShipDesign 配置读取，当前使用 ShipBody 默认值）喵
        // 科技等级加成将在科技系统完成后接入喵

        // 5. 注册到权威世界状态喵
        worldState.registerEntity(ship);

        // TODO AssetManager 统一处理：等归属转移设计完成后，通过 AssetManager.assignToPlayer/assignToNation 分配喵

        return entityId;
    }

    /**
     * 在指定位置生成舰船（通用方法）喵。
     *
     * 策略：
     * - 使用提供的世界坐标和星区坐标（若未提供则自动计算）喵。
     * - 可指定所属星系ID（systemId），0 表示不在任何星系内喵。
     * - 可指定自定义标记集合（customFlags），为空时仅包含基本标记喵。
     * - 硬编码基础属性：耐久 1.0、能源 100、燃料 100喵。
     *
     * @param worldState 权威世界状态（必须非空）
     * @param nationId   所属国家ID（必须非空）
     * @param position   世界坐标（GU，必须非空）
     * @param systemId   所属星系ID（0 表示无）
     * @param customFlags 自定义标记集合（可为空）
     * @return 生成的舰船实体ID，失败时返回 -1
     */
    public static long spawnShipAtPosition(WorldState worldState, String nationId, SpacePosition position,
                                          long systemId, java.util.Set<String> customFlags) {
        if (worldState == null) {
            throw new IllegalArgumentException("worldState_required");
        }
        if (nationId == null || nationId.isBlank()) {
            throw new IllegalArgumentException("nationId_required");
        }
        if (position == null) {
            throw new IllegalArgumentException("position_required");
        }

        // 1. 生成实体ID（全局唯一）喵
        long entityId = worldState.generateEntityId();
        if (entityId <= 0) {
            return -1;
        }

        // 2. 创建舰船实体喵
        ShipBody ship = new ShipBody();
        ship.entityId = entityId;
        ship.entityType = EntityType.SHIP;
        ship.designId = null; // 暂不依赖设计文件喵
        ship.posWorldGU = position;
        ship.velWorldGU = SpacePosition.ORIGIN;
        ship.systemId = systemId;

        // 硬编码基础属性喵
        ship.hpHull = 1.0;      // 满耐久喵
        ship.power = 100.0;     // 初始能源喵
        ship.fuelMass = 100.0;  // 初始燃料喵
        if (customFlags != null) {
            ship.customFlags.addAll(customFlags);
        }

        // 初始化舰船性能数据（从 ShipDesign 配置读取，当前使用 ShipBody 默认值）喵
        // 科技等级加成将在科技系统完成后接入喵

        // 4. 注册到权威世界状态喵
        worldState.registerEntity(ship);

        // TODO AssetManager 统一处理：等归属转移设计完成后，通过 AssetManager.assignToPlayer/assignToNation 分配喵

        return entityId;
    }
}